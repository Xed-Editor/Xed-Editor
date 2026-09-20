package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiChatRole
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiToolCall
import com.rk.ai.model.AiTurn
import com.rk.ai.model.TodoStatus
import com.rk.ai.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatCodecTest {
    private val snapshot =
        AiChatSnapshot(
            messages =
                listOf(
                    AiChatMessage(id = 0, role = AiChatRole.User, text = "explain the build"),
                    AiChatMessage(
                        id = 1,
                        role = AiChatRole.Assistant,
                        text = "It uses Gradle.",
                        reasoning = "The user asked about the build.",
                        isStreaming = true,
                        error = null,
                    ),
                    AiChatMessage(
                        id = 2,
                        role = AiChatRole.Tool,
                        text = "done",
                        toolName = "read_file",
                        toolArgs = """{"path":"build.gradle.kts"}""",
                        toolStatus = ToolCallStatus.Success,
                        diff = "@@\n+added\n",
                        children =
                            listOf(
                                AiChatMessage(
                                    id = 3,
                                    role = AiChatRole.Tool,
                                    text = "nested",
                                    toolName = "glob",
                                    toolArgs = "{}",
                                    toolStatus = ToolCallStatus.Failed,
                                )
                            ),
                    ),
                ),
            history =
                listOf(
                    AiTurn.User("hi"),
                    AiTurn.Assistant("hello", listOf(AiToolCall("call-1", "read_file", "{}"))),
                    AiTurn.ToolOutput("call-1", "read_file", "contents", isError = false),
                ),
            goal = "ship persistence",
            todos = listOf(AiTodo("write the codec", TodoStatus.InProgress), AiTodo("test it", TodoStatus.Pending)),
            draft = "half-written prompt…",
            nextId = 4,
            showReasoning = true,
        )

    @Test
    fun roundTripPreservesEverything() {
        assertEquals(snapshot, AiChatPayload.decode(AiChatPayload.encode(snapshot)))
    }

    @Test
    fun unicodeAndOptionalFieldsSurvive() {
        val tricky =
            AiChatSnapshot(
                messages =
                    listOf(
                        AiChatMessage(
                            id = 7,
                            role = AiChatRole.Assistant,
                            text = "héllo 🌍 — ünïcode",
                            toolName = null,
                            toolStatus = null,
                        )
                    ),
                goal = null,
                todos = emptyList(),
                draft = "",
                nextId = 8,
            )

        assertEquals(tricky, AiChatPayload.decode(AiChatPayload.encode(tricky)))
    }

    @Test
    fun everyToolStatusMapsToOneCode() {
        // A status that encoded to the same code as another would silently change meaning on restore.
        ToolCallStatus.entries.forEach { status ->
            val one = AiChatSnapshot(messages = listOf(toolMessage(status)))
            val restored = AiChatPayload.decode(AiChatPayload.encode(one))
            assertEquals(status, restored?.messages?.single()?.toolStatus)
        }
    }

    @Test
    fun corruptPayloadDecodesToNull() {
        val encoded = AiChatPayload.encode(snapshot)

        assertNull(AiChatPayload.decode(null))
        assertNull(AiChatPayload.decode(ByteArray(0)))
        assertNull("a truncated payload", AiChatPayload.decode(encoded.copyOf(encoded.size / 2)))
        assertNull("garbage", AiChatPayload.decode(ByteArray(32) { 0x7F }))
    }

    @Test
    fun payloadLeadsWithItsVersionSoANewerFormatIsRefusedNotMisread() {
        val encoded = AiChatPayload.encode(snapshot)

        // The first byte is the envelope's version, and it is the only thing standing between a
        // payload from a future build and a misread chat, so the format has to keep it first.
        assertEquals(1, encoded[0].toInt())
        encoded[0] = 99

        assertNull(AiChatPayload.decode(encoded))
    }

    @Test
    fun settledTurnsInFlightCardsIntoInterrupted() {
        val restored =
            AiChatSnapshot(
                    messages =
                        listOf(
                            toolMessage(ToolCallStatus.Running),
                            AiChatMessage(id = 1, role = AiChatRole.Assistant, isStreaming = true),
                        )
                )
                .settled()

        assertEquals(ToolCallStatus.Interrupted, restored.messages[0].toolStatus)
        assertTrue(restored.messages[1].isStreaming.not())
    }

    @Test
    fun aSettledSnapshotIsWhatAStoppedSessionReads() {
        // The tab persists the snapshot it was handed; a tool that was running when the process died
        // must not come back as "running", or the card would spin forever on a dead run.
        val interrupted =
            AiChatPayload.decode(AiChatPayload.encode(AiChatSnapshot(messages = listOf(toolMessage(ToolCallStatus.Running))).settled()))

        assertNotNull(interrupted)
        assertEquals(ToolCallStatus.Interrupted, interrupted?.messages?.single()?.toolStatus)
    }

    private fun toolMessage(status: ToolCallStatus) =
        AiChatMessage(
            id = 0,
            role = AiChatRole.Tool,
            text = "working",
            toolName = "write_file",
            toolArgs = "{}",
            toolStatus = status,
        )
}
