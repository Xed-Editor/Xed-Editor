package com.rk.ai.chat

import ai.koog.prompt.streaming.StreamFrame
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiChatRole
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiToolCall
import com.rk.ai.model.AiTurn
import com.rk.ai.model.PendingApproval
import com.rk.ai.model.PendingQuestion
import com.rk.ai.model.ToolCallStatus
import com.rk.ai.provider.AiProviderRuntime
import com.rk.ai.settings.AiScratchpad
import com.rk.ai.settings.AiSettings
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolRegistry
import com.rk.ai.tools.AiToolSession
import com.rk.ai.tools.AiWorkspace
import com.rk.ai.tools.toDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Owns one chat tab's transcript and runs the agent loop for it.
 *
 * The controller is the only place that knows about approvals, questions, the goal and the task
 * list; tools are looked up in [AiToolRegistry] and either executed directly or handed a
 * [Session] when they need that state. Nothing here is hard-coded to a specific tool name, so a tool
 * registered by an extension is first-class.
 *
 * It also owns the state the chat tab persists: everything the user would expect to still be there
 * after the app is killed lives here, and [snapshot] hands it over in one piece.
 */
class AiChatController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val history = mutableListOf<AiTurn>()

    private var nextId = 0L
    private var runJob: Job? = null
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private var questionDeferred: CompletableDeferred<String>? = null

    private val approvedPaths = mutableSetOf<String>()

    val messages = mutableStateListOf<AiChatMessage>()

    /**
     * Bumped by [markChanged] on every edit to the persisted state.
     *
     * Comparing it against [cachedRevision] is how [snapshot] decides whether the cached payload is
     * still current; the counter is a single `long` comparison for a save that changed nothing,
     * while a real change is encoded exactly once no matter how many tabs are saved.
     */
    private var revision = 0L
    private var cachedRevision = -1L
    private var cachedPayload: ByteArray? = null

    var isRunning by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var pendingApproval by mutableStateOf<PendingApproval?>(null)
        private set

    /** A question the run is blocked on, shown above the composer until it is answered. */
    var pendingQuestion by mutableStateOf<PendingQuestion?>(null)
        private set

    /** The goal the agent declared for this chat, shown to the user above the transcript. */
    var goal by mutableStateOf<String?>(null)
        private set

    /** The agent's task list for this chat, shown to the user above the transcript. */
    var todos by mutableStateOf<List<AiTodo>>(emptyList())
        private set

    // Backing state for the two persisted view inputs. They are private with an explicit accessor
    // rather than `var ... private set`, because a Kotlin setter of that name would clash with the
    // accessor on the JVM; the public half is read-only, so the only way to change them is through
    // the accessor that also flags the change for the next save.
    private var draftState by mutableStateOf("")
    private var reasoningState by mutableStateOf(false)

    /**
     * Text typed into the composer but not sent yet.
     *
     * It lives here rather than in the screen's `rememberSaveable` so it is part of the persisted
     * chat: a half-written prompt survives the process, and it is shared by every recomposition of
     * the tab instead of being reset with the composition.
     */
    val draft: String
        get() = draftState

    /** Whether the model's reasoning is folded out in the transcript; a view preference, persisted. */
    val showReasoning: Boolean
        get() = reasoningState

    init {
        // Extension tools may register before the chat tab is opened; make sure the built-ins are
        // present too so a run never starts with an empty tool set.
        AiToolRegistry.all()
    }

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || isRunning) return

        if (!AiSettings.hasApiKey) {
            val message = "No API key configured. Open AI settings and add one."
            Log.w(TAG, message)
            lastError = message
            return
        }

        lastError = null
        messages.add(AiChatMessage(id = nextId++, role = AiChatRole.User, text = text))
        // The prompt has left the composer and joined the transcript, so both ends of the persisted
        // state changed: the draft is cleared and the transcript has one more message.
        if (draftState.isNotEmpty()) draftState = ""
        markChanged()

        isRunning = true
        runJob =
            scope.launch {
                try {
                    history.add(AiTurn.User(text))
                    runLoop(
                        history = history,
                        transcript = MainTranscript(),
                        depth = 0,
                        basePrompt = AiSettings.systemPrompt,
                    )
                } catch (e: ToolDeniedException) {
                    // The user's rejection already stopped the run; it is not a failure, so no banner.
                    Log.i(TAG, "Run stopped: ${e.message}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = e.message ?: e::class.simpleName ?: "Request failed"
                    Log.e(TAG, "Agent run failed: $message", e)
                    lastError = message
                } finally {
                    isRunning = false
                    runJob = null
                    pendingApproval = null
                    approvalDeferred = null
                    pendingQuestion = null
                    questionDeferred = null
                }
            }
    }

    fun approve(allow: Boolean) {
        approvalDeferred?.complete(allow)
    }

    /** Answers the pending `ask_user` question with either a chosen option or a typed reply. */
    fun answerQuestion(answer: String) {
        questionDeferred?.complete(answer.trim())
    }

    /** Records composer text as the user types it, so it is part of the persisted chat. */
    fun setDraft(text: String) {
        if (draftState == text) return
        draftState = text
        markChanged()
    }

    fun setShowReasoning(show: Boolean) {
        if (reasoningState == show) return
        reasoningState = show
        markChanged()
    }

    /** Dismisses the declared goal; the agent is told about the change on its next turn. */
    fun clearGoal() {
        goal = null
        markChanged()
    }

    /** Dismisses the task list; the agent is told about the change on its next turn. */
    fun clearTodos() {
        todos = emptyList()
        markChanged()
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        isRunning = false
        pendingApproval = null
        approvalDeferred?.cancel()
        approvalDeferred = null
        pendingQuestion = null
        questionDeferred?.cancel()
        questionDeferred = null
        // A cancelled stream never reaches its `finish`, so clear any spinners left behind, including
        // the ones inside sub-agent transcripts.
        for (index in messages.indices) {
            messages[index] = messages[index].stopped()
        }
        // A stopped run leaves tool cards running in the transcript; the difference has to reach the
        // saved chat too, or a restart would show a spinner for a tool that is no longer running.
        markChanged()
    }

    fun clear() {
        stop()
        history.clear()
        messages.clear()
        approvedPaths.clear()
        goal = null
        todos = emptyList()
        lastError = null
        markChanged()
    }

    fun dispose() {
        scope.cancel()
        // The scratch pad lives and dies with the chat tab.
        AiScratchpad.clear()
    }

    /** Flags the persisted state as changed, so the next [snapshot] re-encodes instead of reusing. */
    private fun markChanged() {
        revision++
    }

    /**
     * Everything the chat tab persists, in one piece.
     *
     * The snapshot is built from the live collections - they are immutable models, so handing over the
     * lists costs nothing - and then encoded through [AiChatPayload]. The encoded bytes are kept: a
     * session save that runs after no chat changes (the common case for a background app) returns the
     * same array instead of walking the whole transcript again.
     */
    fun snapshot(): AiChatSnapshot = AiChatSnapshot(
        messages = messages.toList(),
        history = history.toList(),
        goal = goal,
        todos = todos,
        draft = draft,
        nextId = nextId,
        showReasoning = showReasoning,
    )

    /** The encoded form of [snapshot], reused until something changes. */
    private fun snapshotPayload(): ByteArray {
        cachedPayload?.let { if (cachedRevision == revision) return it }

        val payload = AiChatPayload.encode(snapshot())
        cachedPayload = payload
        cachedRevision = revision
        return payload
    }

    /**
     * Replaces the chat with a restored [snapshot].
     *
     * In-flight work never survives a restart, so the restored transcript is settled first: a tool
     * card whose run died with the process is marked [ToolCallStatus.Interrupted] rather than left
     * looking like it is still running. The agent history is restored unchanged - it is what the next
     * request replays - and [nextId] is carried over so message ids stay unique.
     */
    fun applySnapshot(snapshot: AiChatSnapshot) {
        val settled = snapshot.settled()
        messages.clear()
        messages.addAll(settled.messages)
        history.clear()
        history.addAll(settled.history)
        goal = settled.goal
        todos = settled.todos
        draftState = settled.draft
        reasoningState = settled.showReasoning
        nextId = maxOf(settled.nextId, (settled.messages.maxOfOrNull { it.id } ?: -1L) + 1)
        // A restored chat is on disk already; marking it changed only invalidates the (empty) cache.
        markChanged()
    }

    /**
     * The bytes the session stores for this chat.
     *
     * The session file is the one copy of a chat's state: it is written on every save anyway, so a
     * second copy on disk would only mean encoding and writing the whole transcript twice for every
     * pause. This returns the cached encoding when nothing changed since the last save.
     */
    fun persist(): ByteArray = snapshotPayload()

    /**
     * Runs one agent to completion and returns its final answer.
     *
     * The loop is shared by the main agent and by sub-agents; the only differences are the [history]
     * it accumulates, the [transcript] it reports to, and the [basePrompt]. A sub-agent gets a fresh
     * history, which is the whole point: its intermediate tool output never enters the main
     * conversation.
     */
    private suspend fun runLoop(
        history: MutableList<AiTurn>,
        transcript: Transcript,
        depth: Int,
        basePrompt: String,
    ): String {
        repeat(MAX_STEPS) {
            // Rebuilt every step so a goal, task list or memory the agent changed in the previous
            // step is already reflected in the next request.
            val assistant = streamAssistant(history, transcript, buildSystemPrompt(basePrompt, goal, todos), depth)
            history.add(assistant)
            markChanged()
            if (assistant.toolCalls.isEmpty()) return assistant.text

            assistant.toolCalls.forEachIndexed { index, call ->
                val outcome =
                    try {
                        executeTool(call, transcript, depth)
                    } catch (t: Throwable) {
                        // The assistant message that requested these calls is already in the history,
                        // and the API requires a tool result for every `tool_call_id` in it. Without
                        // these, the *next* request fails with "an assistant message with 'tool_calls'
                        // must be followed by tool messages". This covers a user denial, the stop
                        // button cancelling mid-tool, and any unexpected failure.
                        answerRemainingToolCalls(history, assistant.toolCalls, fromIndex = index, cause = t)
                        throw t
                    }
                history.add(AiTurn.ToolOutput(outcome.id, call.name, outcome.output, outcome.isError))
                markChanged()
            }
        }

        val message = "Stopped after $MAX_STEPS steps without a final answer."
        Log.w(TAG, message)
        // Only the main run has an error banner; a sub-agent reports the same text as its result.
        if (depth == 0) lastError = message
        return message
    }

    private suspend fun streamAssistant(
        history: List<AiTurn>,
        transcript: Transcript,
        systemPrompt: String,
        depth: Int,
    ): AiTurn.Assistant {
        val conversation = buildConversationPrompt(systemPrompt = systemPrompt, history = history)
        val messageId = nextId++
        transcript.add(AiChatMessage(id = messageId, role = AiChatRole.Assistant, isStreaming = true))

        // A sub-agent works on its own, so the tools that talk to the user or own session state are
        // withheld from it: it cannot ask questions, and it cannot overwrite the main goal or list.
        val offered = offeredTools(depth)

        val toolCalls = LinkedHashMap<String, AiToolCall>()
        AiProviderRuntime.executor()
            .executeStreaming(conversation, AiProviderRuntime.model(), offered.map { it.toDescriptor() })
            .collect { frame ->
                when (frame) {
                    is StreamFrame.TextDelta -> appendText(transcript, messageId, frame.text)
                    is StreamFrame.ReasoningDelta -> {
                        // Chat-completions providers stream reasoning text; the Responses API can send
                        // a summary instead.
                        val chunk = frame.text ?: frame.summary
                        chunk?.let { appendReasoning(transcript, messageId, it) }
                    }
                    is StreamFrame.ReasoningComplete ->
                        if (frame.content.isNotEmpty()) {
                            setReasoning(transcript, messageId, frame.content.joinToString(""))
                        }
                    is StreamFrame.ToolCallComplete -> {
                        val key = frame.id ?: "index-${frame.index ?: toolCalls.size}"
                        val callId = frame.id ?: toolCalls[key]?.id ?: UUID.randomUUID().toString()
                        toolCalls[key] = AiToolCall(callId, frame.name, frame.content)
                    }
                    is StreamFrame.End -> finish(transcript, messageId)
                    else -> Unit
                }
            }
        finish(transcript, messageId)

        if (toolCalls.isNotEmpty()) {
            Log.i(
                TAG,
                "Model requested ${toolCalls.size} tool call(s): " + toolCalls.values.joinToString { it.name },
            )
        }

        return AiTurn.Assistant(
            text = transcript.get(messageId)?.text.orEmpty(),
            toolCalls = toolCalls.values.toList(),
        )
    }

    private fun offeredTools(depth: Int): List<AiTool> {
        val all = AiToolRegistry.all()
        return if (depth == 0) all else all.filterNot { it.mainAgentOnly }
    }

    private suspend fun executeTool(call: AiToolCall, transcript: Transcript, depth: Int): ToolOutcome {
        val tool =
            AiToolRegistry.find(call.name)
                ?: run {
                    Log.e(TAG, "Model requested unknown tool '${call.name}'")
                    return ToolOutcome(call.id, "Unknown tool '${call.name}'.", true)
                }

        val mode = AiSettings.currentPermissionMode()
        val args = parseArgs(call.args)
        val target = targetKey(tool, args)
        val alreadyApproved = target != null && target in approvedPaths
        val diff = computeDiff(tool, args)

        when (gateFor(tool, mode, alreadyApproved = alreadyApproved)) {
            ToolGate.RunNow -> {
                if (!tool.isDestructive) {
                    Log.i(TAG, "Auto-running non-destructive tool '${tool.name}'")
                } else if (alreadyApproved) {
                    Log.i(TAG, "Auto-running '${tool.name}': '$target' was already allowed in this chat")
                }
                val status = if (alreadyApproved) "Running (already allowed for this file)…" else "Running…"
                val id = addToolMessage(transcript, tool.name, call.args, ToolCallStatus.Running, status, diff)
                return runTool(transcript, tool, call, args, id, depth)
            }

            ToolGate.Blocked -> {
                Log.i(TAG, "Blocked tool '${tool.name}' (plan mode)")
                val id =
                    addToolMessage(
                        transcript,
                        tool.name,
                        call.args,
                        ToolCallStatus.Denied,
                        "Plan mode: writes and shell commands are disabled.",
                        diff,
                    )
                return ToolOutcome(call.id, transcript.get(id)?.text.orEmpty(), true)
            }

            ToolGate.AskUser -> Unit
        }

        val id =
            addToolMessage(
                transcript,
                tool.name,
                call.args,
                ToolCallStatus.AwaitingApproval,
                "Waiting for approval…",
                diff,
            )
        Log.i(TAG, "Tool '${tool.name}' awaiting approval (mode=$mode, args=${call.args.snippet()})")
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        pendingApproval = PendingApproval(toolName = tool.name)
        val allowed =
            try {
                deferred.await()
            } finally {
                pendingApproval = null
                approvalDeferred = null
            }

        if (!allowed) {
            Log.i(TAG, "Tool '${tool.name}' denied by user; stopping the run")
            transcript.update(id) { it.copy(toolStatus = ToolCallStatus.Denied, text = "Denied by user.") }
            // Rejecting a tool must end the turn. Handing the denial back to the model invites it to
            // ask for the same permission again, which is what left the approval prompt reappearing
            // after every "deny".
            throw ToolDeniedException(tool.name)
        }

        if (target != null) {
            approvedPaths.add(target)
            Log.i(TAG, "File '$target' allowed for the rest of this chat")
        }

        transcript.update(id) { it.copy(toolStatus = ToolCallStatus.Running, text = "Running…") }
        return runTool(transcript, tool, call, args, id, depth)
    }

    /**
     * Runs a tool and records its outcome on the card created for it.
     *
     * A plain tool runs on [Dispatchers.IO]; a session tool runs on the agent dispatcher because it
     * reads and writes the controller's Compose state.
     */
    private suspend fun runTool(
        transcript: Transcript,
        tool: AiTool,
        call: AiToolCall,
        args: JsonObject,
        messageId: Long,
        depth: Int,
    ): ToolOutcome {
        Log.i(TAG, "Running tool '${tool.name}' with args=${call.args.snippet()}")
        return try {
            val handler = tool.handler
            val output =
                if (handler != null) {
                    handler.invoke(args, Session(transcript, messageId, depth))
                } else {
                    withContext(Dispatchers.IO) { tool.execute?.invoke(args).orEmpty() }
                }
            transcript.update(messageId) { it.copy(toolStatus = ToolCallStatus.Success, text = output) }
            Log.i(TAG, "Tool '${tool.name}' succeeded (${output.length} chars)")
            ToolOutcome(call.id, output, false)
        } catch (e: ToolDeniedException) {
            // A denial raised deeper down (a sub-agent's tool call) stops the whole run; settle this
            // card so it does not keep a spinner on the way out.
            transcript.update(messageId) {
                it.copy(toolStatus = ToolCallStatus.Denied, text = "Stopped: the user denied a tool.")
            }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "Tool failed"
            Log.e(TAG, "Tool '${tool.name}' failed: $message", e)
            transcript.update(messageId) { it.copy(toolStatus = ToolCallStatus.Failed, text = message) }
            ToolOutcome(call.id, message, true)
        }
    }

    private fun targetKey(tool: AiTool, args: JsonObject): String? {
        val path = tool.targetPath?.invoke(args)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { AiWorkspace.normalizePath(path) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun computeDiff(tool: AiTool, args: JsonObject): String? {
        val preview = tool.preview ?: return null
        return runCatching { withContext(Dispatchers.IO) { preview(args) } }
            .onFailure { Log.w(TAG, "Diff preview failed for tool '${tool.name}'", it) }
            .getOrNull()
    }

    private fun addToolMessage(
        transcript: Transcript,
        toolName: String,
        args: String,
        status: ToolCallStatus,
        text: String,
        diff: String? = null,
    ): Long {
        val id = nextId++
        transcript.add(
            AiChatMessage(
                id = id,
                role = AiChatRole.Tool,
                text = text,
                toolName = toolName,
                toolArgs = args,
                toolStatus = status,
                diff = diff,
            )
        )
        return id
    }

    private fun parseArgs(args: String): JsonObject {
        if (args.isBlank()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(args).jsonObject }
            .onFailure { Log.w(TAG, "Malformed tool arguments, using empty object: ${args.snippet()}", it) }
            .getOrElse { JsonObject(emptyMap()) }
    }

    private fun String.snippet(): String = if (length <= SNIPPET_CHARS) this else take(SNIPPET_CHARS) + "…($length chars)"

    private fun appendText(transcript: Transcript, id: Long, chunk: String) {
        if (chunk.isEmpty()) return
        transcript.update(id) { it.copy(text = it.text + chunk) }
    }

    private fun appendReasoning(transcript: Transcript, id: Long, chunk: String) {
        if (chunk.isEmpty()) return
        transcript.update(id) { it.copy(reasoning = it.reasoning + chunk) }
    }

    private fun setReasoning(transcript: Transcript, id: Long, reasoning: String) {
        transcript.update(id) { it.copy(reasoning = reasoning) }
    }

    private fun finish(transcript: Transcript, id: Long) {
        transcript.update(id) { it.copy(isStreaming = false) }
    }

    /**
     * The session a tool handler runs inside.
     *
     * It is bound to the tool card that is currently running so [updateCall] can settle it, and to
     * the run's [depth] so sub-agent nesting is bounded.
     */
    private inner class Session(
        private val transcript: Transcript,
        private val messageId: Long,
        override val depth: Int,
    ) : AiToolSession {
        override fun updateCall(status: ToolCallStatus, text: String) {
            transcript.update(messageId) { it.copy(toolStatus = status, text = text) }
        }

        override suspend fun askUser(question: String, options: List<String>): String {
            val deferred = CompletableDeferred<String>()
            questionDeferred = deferred
            pendingQuestion = PendingQuestion(question = question, options = options)
            Log.i(TAG, "Waiting for the user to answer: ${question.snippet()}")
            return try {
                deferred.await()
            } finally {
                pendingQuestion = null
                questionDeferred = null
            }
        }

        override fun setGoal(goal: String?) {
            this@AiChatController.goal = goal
            markChanged()
        }

        override fun setTodos(todos: List<AiTodo>) {
            this@AiChatController.todos = todos
            markChanged()
        }

        override suspend fun runSubAgent(prompt: String): String {
            if (depth >= MAX_AGENT_DEPTH) {
                throw IllegalArgumentException("Sub-agents cannot be nested more than $MAX_AGENT_DEPTH levels deep.")
            }
            // A sub-agent shares the tool set, workspace and approval state, but not the
            // conversation: it only sees the prompt it was given, so the main context stays clean.
            val childHistory = mutableListOf<AiTurn>(AiTurn.User(prompt))
            val childTranscript = ChildTranscript(messageId)
            Log.i(TAG, "Spawning sub-agent (depth=${depth + 1}): ${prompt.snippet()}")
            return runLoop(childHistory, childTranscript, depth + 1, SUB_AGENT_SYSTEM_PROMPT)
                .ifBlank { "The sub-agent finished without producing an answer." }
        }
    }

    private data class ToolOutcome(val id: String, val output: String, val isError: Boolean)

    /**
     * Where one agent run reports the turns it produces.
     *
     * The main run writes straight to [messages]. A sub-agent writes into the `children` of the tool
     * call that launched it, so its work stays nested under that call instead of being interleaved
     * into the main transcript.
     */
    private interface Transcript {
        fun add(message: AiChatMessage)

        fun get(id: Long): AiChatMessage?

        fun update(id: Long, transform: (AiChatMessage) -> AiChatMessage)
    }

    private inner class MainTranscript : Transcript {
        override fun add(message: AiChatMessage) {
            messages.add(message)
            markChanged()
        }

        override fun get(id: Long): AiChatMessage? = messages.firstOrNull { it.id == id }

        override fun update(id: Long, transform: (AiChatMessage) -> AiChatMessage) {
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                messages[index] = transform(messages[index])
                // Every transcript edit - a streamed token, a tool card settling, a sub-agent step -
                // reaches the persisted state through here, so this is the one place that has to
                // notice it.
                markChanged()
            }
        }
    }

    private inner class ChildTranscript(private val parentId: Long) : Transcript {
        override fun add(message: AiChatMessage) {
            mutateChildren { it + message }
        }

        override fun get(id: Long): AiChatMessage? = children().firstOrNull { it.id == id }

        override fun update(id: Long, transform: (AiChatMessage) -> AiChatMessage) {
            mutateChildren { children -> children.map { if (it.id == id) transform(it) else it } }
        }

        private fun children(): List<AiChatMessage> = messages.firstOrNull { it.id == parentId }?.children.orEmpty()

        private fun mutateChildren(block: (List<AiChatMessage>) -> List<AiChatMessage>) {
            val index = messages.indexOfFirst { it.id == parentId }
            if (index >= 0) {
                messages[index] = messages[index].copy(children = block(messages[index].children))
                markChanged()
            }
        }
    }

    private companion object {
        const val MAX_STEPS = 8

        /** How deep sub-agents may nest: 1 is a direct child of the main agent. */
        const val MAX_AGENT_DEPTH = 2

        const val TAG = "XedAI"

        const val SNIPPET_CHARS = 500
    }
}

private const val SUB_AGENT_SYSTEM_PROMPT =
    "You are a sub-agent working on a single focused task for the main assistant. " +
        "You cannot ask the user questions: if something is ambiguous, pick the most reasonable " +
        "interpretation, carry on, and say what you assumed. Use the available tools to do the work, " +
        "then finish with a concise report - what you found or changed, the exact file paths " +
        "involved, and anything the main assistant needs in order to continue. Do not pad the report."
