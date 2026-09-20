package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiChatRole
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiToolCall
import com.rk.ai.model.AiTurn
import com.rk.ai.model.TodoStatus
import com.rk.ai.model.ToolCallStatus

/**
 * Wire format behind [AiChatSnapshot.encode]. Varints are unsigned LEB128, text is UTF-8 and
 * integers are big-endian:
 * ```
 * version   : uint8   (written by AiChatPayload)
 * magic     : int32   ('XEDA')
 * messages  : varint count, then per message:
 *               id varint, role uint8 (1 user, 2 assistant, 3 tool), text string,
 *               reasoning string, streaming uint8, error string?, toolName string?,
 *               toolArgs string?, toolStatus uint8 (0 none, else 1..7), diff string?,
 *               children varint count, then messages
 * history   : varint count, then per turn:
 *               tag uint8 (1 user, 2 assistant, 3 tool output)
 *               user: text
 *               assistant: text, varint count of calls { id, name, args }
 *               tool output: id, name, output, error uint8
 * goal      : string?
 * draft     : string
 * todos     : varint count, then per todo { content string, status uint8 }
 * nextId    : varint
 * reasoning : uint8   (0 or 1)
 * string    : varint byte length, then UTF-8 bytes
 * ```
 * Enums use explicit codes so reordering cannot reinterpret an old payload, and the reader ignores
 * trailing bytes so later versions can append fields.
 */
internal object AiChatCodec {
    private const val MAGIC = 0x58454441 // "XEDA"

    fun encode(snapshot: AiChatSnapshot, version: Int): ByteArray {
        val writer = ChatWriter(snapshot.estimatedSize())
        writer.byte(version)
        writer.int(MAGIC)

        writer.varInt(snapshot.messages.size)
        snapshot.messages.forEach { writeMessage(writer, it) }

        writer.varInt(snapshot.history.size)
        snapshot.history.forEach { writeTurn(writer, it) }

        writer.nullableString(snapshot.goal)
        writer.string(snapshot.draft)

        writer.varInt(snapshot.todos.size)
        snapshot.todos.forEach {
            writer.string(it.content)
            writer.byte(todoStatusCode(it.status))
        }

        writer.varLong(snapshot.nextId)
        writer.boolean(snapshot.showReasoning)
        return writer.toByteArray()
    }

    /** Returns null for anything this build cannot read. */
    fun decode(payload: ByteArray, version: Int): AiChatSnapshot? =
        runCatching { read(payload, version) }.getOrNull()

    private fun read(payload: ByteArray, version: Int): AiChatSnapshot {
        val reader = ChatReader(payload)
        if (reader.byte() != version) throw CorruptPayload()
        if (reader.int() != MAGIC) throw CorruptPayload()

        val messages = ArrayList<AiChatMessage>()
        repeat(reader.count()) { messages.add(readMessage(reader)) }

        val history = ArrayList<AiTurn>()
        repeat(reader.count()) { history.add(readTurn(reader)) }

        val goal = reader.nullableString()
        val draft = reader.string()

        val todos = ArrayList<AiTodo>()
        repeat(reader.count()) { todos.add(AiTodo(reader.string(), todoStatusFrom(reader.byte()))) }

        val nextId = reader.varLong()
        val showReasoning = reader.boolean()
        return AiChatSnapshot(
            messages = messages,
            history = history,
            goal = goal,
            todos = todos,
            draft = draft,
            nextId = nextId,
            showReasoning = showReasoning,
        )
    }

    private fun readMessage(reader: ChatReader): AiChatMessage {
        val id = reader.varLong()
        val role = roleFrom(reader.byte())
        val text = reader.string()
        val reasoning = reader.string()
        val isStreaming = reader.boolean()
        val error = reader.nullableString()
        val toolName = reader.nullableString()
        val toolArgs = reader.nullableString()
        val statusCode = reader.byte()
        val toolStatus = if (statusCode == 0) null else toolStatusFrom(statusCode)
        val diff = reader.nullableString()

        val children = ArrayList<AiChatMessage>()
        repeat(reader.count()) { children.add(readMessage(reader)) }

        return AiChatMessage(
            id = id,
            role = role,
            text = text,
            reasoning = reasoning,
            isStreaming = isStreaming,
            error = error,
            toolName = toolName,
            toolArgs = toolArgs,
            toolStatus = toolStatus,
            diff = diff,
            children = children,
        )
    }

    private fun readTurn(reader: ChatReader): AiTurn =
        when (val tag = reader.byte()) {
            1 -> AiTurn.User(reader.string())
            2 -> {
                val text = reader.string()
                val calls = ArrayList<AiToolCall>()
                repeat(reader.count()) {
                    val id = reader.string()
                    val name = reader.string()
                    val args = reader.string()
                    calls.add(AiToolCall(id, name, args))
                }
                AiTurn.Assistant(text, calls)
            }
            3 -> {
                val id = reader.string()
                val name = reader.string()
                val output = reader.string()
                AiTurn.ToolOutput(id, name, output, reader.boolean())
            }
            else -> throw CorruptPayload("Unknown history turn tag $tag")
        }

    private fun writeMessage(writer: ChatWriter, message: AiChatMessage) {
        writer.varLong(message.id)
        writer.byte(roleCode(message.role))
        writer.string(message.text)
        writer.string(message.reasoning)
        writer.boolean(message.isStreaming)
        writer.nullableString(message.error)
        writer.nullableString(message.toolName)
        writer.nullableString(message.toolArgs)
        writer.byte(message.toolStatus?.let(::toolStatusCode) ?: 0)
        writer.nullableString(message.diff)
        writer.varInt(message.children.size)
        message.children.forEach { writeMessage(writer, it) }
    }

    private fun writeTurn(writer: ChatWriter, turn: AiTurn) {
        when (turn) {
            is AiTurn.User -> {
                writer.byte(1)
                writer.string(turn.text)
            }
            is AiTurn.Assistant -> {
                writer.byte(2)
                writer.string(turn.text)
                writer.varInt(turn.toolCalls.size)
                turn.toolCalls.forEach {
                    writer.string(it.id)
                    writer.string(it.name)
                    writer.string(it.args)
                }
            }
            is AiTurn.ToolOutput -> {
                writer.byte(3)
                writer.string(turn.id)
                writer.string(turn.name)
                writer.string(turn.output)
                writer.boolean(turn.isError)
            }
        }
    }

    /** One pass over the sizes avoids repeated buffer growth; UTF-8 is at most three bytes per char. */
    private fun AiChatSnapshot.estimatedSize(): Int {
        var size = 96 + draft.length + (goal?.length ?: 0)
        messages.forEach { size += it.estimatedSize() }
        history.forEach { size += it.estimatedSize() }
        todos.forEach { size += it.content.length + 8 }
        return size * BYTE_PER_CHAR
    }

    private fun AiChatMessage.estimatedSize(): Int =
        ESTIMATED_MESSAGE_OVERHEAD +
            text.length +
            reasoning.length +
            (error?.length ?: 0) +
            (toolName?.length ?: 0) +
            (toolArgs?.length ?: 0) +
            (diff?.length ?: 0) +
            children.sumOf { it.estimatedSize() }

    private fun AiTurn.estimatedSize(): Int =
        when (this) {
            is AiTurn.User -> text.length + ESTIMATED_TURN_OVERHEAD
            is AiTurn.Assistant ->
                text.length +
                    ESTIMATED_TURN_OVERHEAD +
                    toolCalls.sumOf { it.id.length + it.name.length + it.args.length + 8 }
            is AiTurn.ToolOutput ->
                id.length + name.length + output.length + ESTIMATED_TURN_OVERHEAD
        }

    private fun roleCode(role: AiChatRole): Int =
        when (role) {
            AiChatRole.User -> 1
            AiChatRole.Assistant -> 2
            AiChatRole.Tool -> 3
        }

    private fun roleFrom(code: Int): AiChatRole =
        when (code) {
            1 -> AiChatRole.User
            2 -> AiChatRole.Assistant
            3 -> AiChatRole.Tool
            else -> throw CorruptPayload("Unknown role code $code")
        }

    private fun toolStatusCode(status: ToolCallStatus): Int =
        when (status) {
            ToolCallStatus.AwaitingApproval -> 1
            ToolCallStatus.AwaitingInput -> 2
            ToolCallStatus.Running -> 3
            ToolCallStatus.Success -> 4
            ToolCallStatus.Failed -> 5
            ToolCallStatus.Denied -> 6
            ToolCallStatus.Interrupted -> 7
        }

    private fun toolStatusFrom(code: Int): ToolCallStatus =
        when (code) {
            1 -> ToolCallStatus.AwaitingApproval
            2 -> ToolCallStatus.AwaitingInput
            3 -> ToolCallStatus.Running
            4 -> ToolCallStatus.Success
            5 -> ToolCallStatus.Failed
            6 -> ToolCallStatus.Denied
            7 -> ToolCallStatus.Interrupted
            else -> throw CorruptPayload("Unknown tool status code $code")
        }

    private fun todoStatusCode(status: TodoStatus): Int =
        when (status) {
            TodoStatus.Pending -> 1
            TodoStatus.InProgress -> 2
            TodoStatus.Completed -> 3
        }

    private fun todoStatusFrom(code: Int): TodoStatus =
        when (code) {
            1 -> TodoStatus.Pending
            2 -> TodoStatus.InProgress
            3 -> TodoStatus.Completed
            else -> throw CorruptPayload("Unknown todo status code $code")
        }

    private const val ESTIMATED_MESSAGE_OVERHEAD = 28
    private const val ESTIMATED_TURN_OVERHEAD = 12
    private const val BYTE_PER_CHAR = 3
}

/** Thrown for anything malformed; [AiChatCodec.decode] turns it into null. */
private class CorruptPayload(message: String = "Corrupt chat payload") : Exception(message)

/** Stops a corrupt count from turning into a huge allocation. */
private const val MAX_ITEMS = 1_000_000

private class ChatWriter(initialCapacity: Int) {
    private var buffer = ByteArray(initialCapacity.coerceIn(64, MAX_INITIAL_CAPACITY))
    private var size = 0

    fun byte(value: Int) {
        grow(1)
        buffer[size++] = value.toByte()
    }

    fun boolean(value: Boolean) = byte(if (value) 1 else 0)

    fun int(value: Int) {
        grow(4)
        buffer[size++] = (value ushr 24).toByte()
        buffer[size++] = (value ushr 16).toByte()
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun varInt(value: Int) {
        var remaining = value
        while (true) {
            if (remaining and 0x7F.inv() == 0) {
                byte(remaining)
                return
            }
            byte((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
    }

    fun varLong(value: Long) {
        var remaining = value
        while (true) {
            if (remaining and 0x7FL.inv() == 0L) {
                byte(remaining.toInt())
                return
            }
            byte(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
    }

    fun string(value: String) {
        val bytes = value.encodeToByteArray()
        varInt(bytes.size)
        grow(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun nullableString(value: String?) {
        if (value == null) {
            byte(0)
        } else {
            byte(1)
            string(value)
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun grow(count: Int) {
        val needed = size + count
        if (needed <= buffer.size) return
        // Grow by half: a message is small, so a whole transcript costs a couple of copies.
        buffer = buffer.copyOf(maxOf(needed, buffer.size + (buffer.size shr 1)))
    }

    private companion object {
        const val MAX_INITIAL_CAPACITY = 1 shl 24
    }
}

/** Reads the format's primitives; anything past the end throws. */
private class ChatReader(private val payload: ByteArray) {
    private var position = 0

    fun int(): Int {
        require(4)
        val value =
            ((payload[position].toInt() and 0xFF) shl 24) or
                ((payload[position + 1].toInt() and 0xFF) shl 16) or
                ((payload[position + 2].toInt() and 0xFF) shl 8) or
                (payload[position + 3].toInt() and 0xFF)
        position += 4
        return value
    }

    fun byte(): Int {
        require(1)
        return payload[position++].toInt() and 0xFF
    }

    fun boolean(): Boolean = byte() != 0

    fun varInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val next = byte()
            result = result or ((next and 0x7F) shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
            if (shift > 28) throw CorruptPayload("Varint is too long")
        }
    }

    fun varLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val next = byte().toLong()
            result = result or ((next and 0x7F) shl shift)
            if (next and 0x80L == 0L) return result
            shift += 7
            if (shift > 63) throw CorruptPayload("Varint is too long")
        }
    }

    fun count(): Int {
        val value = varInt()
        if (value < 0 || value > MAX_ITEMS) throw CorruptPayload("Unreasonable item count $value")
        return value
    }

    fun string(): String {
        val length = varInt()
        // Checked before allocating, so a corrupt length cannot ask for gigabytes.
        if (length < 0 || length > payload.size - position) throw CorruptPayload("String runs past the end")
        val value = String(payload, position, length, Charsets.UTF_8)
        position += length
        return value
    }

    fun nullableString(): String? = if (byte() == 0) null else string()

    private fun require(count: Int) {
        if (payload.size - position < count) throw CorruptPayload("Payload ended early")
    }
}

/** Reads a chat from session bytes, or null when they are not a chat this build knows. */
fun decodeChatSnapshot(payload: ByteArray?): AiChatSnapshot? = AiChatPayload.decode(payload)

/**
 * The version byte around one encoded chat: a format this build cannot read is dropped instead of
 * failing the whole session restore.
 */
internal object AiChatPayload {
    private const val PAYLOAD_VERSION = 1

    /** The version is handed to the codec rather than prepended, to avoid a second copy per save. */
    fun encode(snapshot: AiChatSnapshot): ByteArray = AiChatCodec.encode(snapshot, PAYLOAD_VERSION)

    fun decode(payload: ByteArray?): AiChatSnapshot? {
        if (payload == null || payload.size < 2) return null
        if (payload[0].toInt() != PAYLOAD_VERSION) return null
        return AiChatCodec.decode(payload, PAYLOAD_VERSION)
    }
}
