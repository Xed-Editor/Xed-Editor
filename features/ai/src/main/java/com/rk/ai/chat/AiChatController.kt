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

/** Owns one chat tab's transcript, agent loop and persisted state. */
class AiChatController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val history = mutableListOf<AiTurn>()

    private var nextId = 0L
    private var runJob: Job? = null
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private var questionDeferred: CompletableDeferred<String>? = null

    private val approvedPaths = mutableSetOf<String>()

    val messages = mutableStateListOf<AiChatMessage>()

    /** Bumped by [markChanged]; invalidates [cachedPayload]. */
    private var revision = 0L
    private var cachedRevision = -1L
    private var cachedPayload: ByteArray? = null

    var isRunning by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var pendingApproval by mutableStateOf<PendingApproval?>(null)
        private set

    var pendingQuestion by mutableStateOf<PendingQuestion?>(null)
        private set

    var goal by mutableStateOf<String?>(null)
        private set

    var todos by mutableStateOf<List<AiTodo>>(emptyList())
        private set

    // Backing fields: `var ... private set` would clash with the JVM accessor of the same name.
    private var draftState by mutableStateOf("")
    private var reasoningState by mutableStateOf(false)

    /** Composer text that has not been sent; persists with the chat. */
    val draft: String
        get() = draftState

    /** Whether the model's reasoning is folded out; persisted. */
    val showReasoning: Boolean
        get() = reasoningState

    init {
        // `all()` installs the built-ins, in case no extension registered them first.
        AiToolRegistry.all()
    }

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || isRunning) return

        if (!AiProviderRuntime.hasApiKey()) {
            val message =
                "No API key configured for ${AiProviderRuntime.activeProvider().displayName}. " +
                    "Add one in AI settings."
            Log.w(TAG, message)
            lastError = message
            return
        }

        lastError = null
        messages.add(AiChatMessage(id = nextId++, role = AiChatRole.User, text = text))
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
                    // A denial already stopped the run cleanly; no error banner.
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

    fun answerQuestion(answer: String) {
        questionDeferred?.complete(answer.trim())
    }

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

    /** The agent is told about the cleared goal on its next turn. */
    fun clearGoal() {
        goal = null
        markChanged()
    }

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
        // A cancelled stream never emits `finish`, so settle the spinners it left behind.
        for (index in messages.indices) {
            messages[index] = messages[index].stopped()
        }
        // Persist the settled cards, or a restart shows spinners for tools that already stopped.
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
        AiScratchpad.clear()
    }

    private fun markChanged() {
        revision++
    }

    /** Everything the chat tab persists, in one piece. */
    fun snapshot(): AiChatSnapshot = AiChatSnapshot(
        messages = messages.toList(),
        history = history.toList(),
        goal = goal,
        todos = todos,
        draft = draft,
        nextId = nextId,
        showReasoning = showReasoning,
    )

    private fun snapshotPayload(): ByteArray {
        cachedPayload?.let { if (cachedRevision == revision) return it }

        val payload = AiChatPayload.encode(snapshot())
        cachedPayload = payload
        cachedRevision = revision
        return payload
    }

    /** Replaces the chat with a restored snapshot, settling work that did not survive the restart. */
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
        // Invalidates the cache so the restored chat is re-encoded on the next save.
        markChanged()
    }

    /** The bytes the session stores for this chat. */
    fun persist(): ByteArray = snapshotPayload()

    /** Runs one agent to completion; shared by the main agent and sub-agents. */
    private suspend fun runLoop(
        history: MutableList<AiTurn>,
        transcript: Transcript,
        depth: Int,
        basePrompt: String,
    ): String {
        repeat(MAX_STEPS) {
            // Rebuilt every step so a goal, task list or memory changed by the agent is picked up.
            val assistant = streamAssistant(history, transcript, buildSystemPrompt(basePrompt, goal, todos), depth)
            history.add(assistant)
            markChanged()
            if (assistant.toolCalls.isEmpty()) return assistant.text

            assistant.toolCalls.forEachIndexed { index, call ->
                val outcome =
                    try {
                        executeTool(call, transcript, depth)
                    } catch (t: Throwable) {
                        // The API requires a result for every `tool_call_id` in the assistant message,
                        // or the next request is rejected; cover denial, cancellation and failure.
                        answerRemainingToolCalls(history, assistant.toolCalls, fromIndex = index, cause = t)
                        throw t
                    }
                history.add(AiTurn.ToolOutput(outcome.id, call.name, outcome.output, outcome.isError))
                markChanged()
            }
        }

        val message = "Stopped after $MAX_STEPS steps without a final answer."
        Log.w(TAG, message)
        // Only the main run has an error banner; a sub-agent returns the text as its result.
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

        // A sub-agent is withheld the tools that talk to the user or own session state.
        val offered = offeredTools(depth)

        val toolCalls = LinkedHashMap<String, AiToolCall>()
        AiProviderRuntime.executor()
            .executeStreaming(conversation, AiProviderRuntime.model(), offered.map { it.toDescriptor() })
            .collect { frame ->
                when (frame) {
                    is StreamFrame.TextDelta -> appendText(transcript, messageId, frame.text)
                    is StreamFrame.ReasoningDelta -> {
                        // Chat-completions providers stream reasoning text; the Responses API may not.
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
        val disabled = AiSettings.disabledTools
        val all = AiToolRegistry.all().filterNot { it.name in disabled }
        return if (depth == 0) all else all.filterNot { it.mainAgentOnly }
    }

    private suspend fun executeTool(call: AiToolCall, transcript: Transcript, depth: Int): ToolOutcome {
        val tool =
            AiToolRegistry.find(call.name)
                ?: run {
                    Log.e(TAG, "Model requested unknown tool '${call.name}'")
                    return ToolOutcome(call.id, "Unknown tool '${call.name}'.", true)
                }

        if (!AiSettings.isToolEnabled(tool.name)) {
            Log.i(TAG, "Model requested disabled tool '${tool.name}'")
            return ToolOutcome(call.id, "Tool '${tool.name}' is disabled in settings.", true)
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
            // End the turn instead of reporting the denial: the model would just ask again, leaving
            // the approval prompt reappearing after every "deny".
            throw ToolDeniedException(tool.name)
        }

        if (target != null) {
            approvedPaths.add(target)
            Log.i(TAG, "File '$target' allowed for the rest of this chat")
        }

        transcript.update(id) { it.copy(toolStatus = ToolCallStatus.Running, text = "Running…") }
        return runTool(transcript, tool, call, args, id, depth)
    }

    /** Session tools run on the agent dispatcher because they touch the controller's Compose state. */
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
            // A denial raised deeper down stops the whole run; settle this card on the way out.
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

    /** The session a tool handler runs inside, bound to the running card and the run's [depth]. */
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
            // A sub-agent shares tools, workspace and approvals, but starts with a fresh history.
            val childHistory = mutableListOf<AiTurn>(AiTurn.User(prompt))
            val childTranscript = ChildTranscript(messageId)
            Log.i(TAG, "Spawning sub-agent (depth=${depth + 1}): ${prompt.snippet()}")
            return runLoop(childHistory, childTranscript, depth + 1, SUB_AGENT_SYSTEM_PROMPT)
                .ifBlank { "The sub-agent finished without producing an answer." }
        }
    }

    private data class ToolOutcome(val id: String, val output: String, val isError: Boolean)

    /** Where one agent run reports its turns: the main transcript or a tool card's children. */
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
