package com.rk.ai.runtime

import android.util.Log
import com.rk.ai.AgentCli
import com.rk.ai.IdeBridge
import com.rk.ai.agents.AiAgent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentRuntime(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    companion object {
        private const val TAG = "AgentRuntime"
        private val _instance = MutableStateFlow<AgentRuntime?>(null)
        val instance: StateFlow<AgentRuntime?> = _instance.asStateFlow()

        fun initialize(): AgentRuntime {
            val rt = AgentRuntime()
            _instance.value = rt
            return rt
        }

        fun get(): AgentRuntime? = _instance.value
    }

    private val sessions = ConcurrentHashMap<String, SessionHandle>()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    fun createSession(config: AgentSessionConfig): String {
        val id = "session-${UUID.randomUUID().toString().take(8)}"
        val handle = SessionHandle(id, config, scope)
        sessions[id] = handle
        _currentSessionId.value = id
        Log.d(TAG, "Created session $id")
        return id
    }

    fun getSession(sessionId: String? = null): SessionHandle? {
        val id = sessionId ?: _currentSessionId.value ?: return null
        return sessions[id]
    }

    fun destroySession(sessionId: String) {
        sessions[sessionId]?.cancel()
        sessions.remove(sessionId)
        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = sessions.keys.lastOrNull()
        }
    }

    fun destroyAll() {
        sessions.values.forEach { it.cancel() }
        sessions.clear()
        _currentSessionId.value = null
    }

    class SessionHandle(
        val id: String,
        val config: AgentSessionConfig,
        private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow(AgentSessionState(
            id = id,
            config = config,
            startedAt = System.currentTimeMillis(),
        ))
        val state: StateFlow<AgentSessionState> = _state.asStateFlow()

        private val eventChannel = Channel<StreamEvent>(Channel.BUFFERED)
        val events: Flow<StreamEvent> = eventChannel.consumeAsFlow()

        private var executionJob: Job? = null
        private val _cancelled = AtomicBoolean(false)
        val isActive: Boolean get() = executionJob?.isActive == true && !_cancelled.get()

        fun execute(prompt: String) {
            if (executionJob?.isActive == true) {
                cancel()
            }
            _cancelled.set(false)
            _state.update { AgentSessionState(id = id, config = config, startedAt = System.currentTimeMillis()) }

            executionJob = scope.launch {
                _state.update { it.copy(phase = SessionPhase.RUNNING) }
                emitEvent(StreamEvent.StreamStart)

                try {
                    runCliAgent(prompt)
                } catch (e: CancellationException) {
                    _state.update { it.copy(phase = SessionPhase.DONE, isCancelled = true) }
                    emitEvent(StreamEvent.Done(finishReason = "cancelled"))
                } catch (e: Exception) {
                    Log.e(TAG, "Execution failed", e)
                    _state.update { it.copy(phase = SessionPhase.ERROR, error = e.message) }
                    emitEvent(StreamEvent.Error(e.message ?: "Unknown error"))
                }
            }
        }

        fun cancel() {
            _cancelled.set(true)
            executionJob?.cancel()
            _state.update { it.copy(phase = SessionPhase.CANCELLING, isCancelled = true) }
        }

        fun sendInput(input: String) {
            val currentPhase = _state.value.phase
            if (currentPhase == SessionPhase.IDLE || currentPhase == SessionPhase.DONE) {
                execute(input)
            }
        }

        private suspend fun runCliAgent(prompt: String) {
            val agent = config.agent
            if (agent == null) {
                emitEvent(StreamEvent.Error("No agent configured"))
                _state.update { it.copy(phase = SessionPhase.ERROR, error = "No agent configured") }
                return
            }

            emitEvent(StreamEvent.Status("Running ${agent.displayName}..."))

            val bridgeInfo = IdeBridge.getBridgeInfo()
            val result = AgentCli.runAgent(
                prompt = prompt,
                agent = agent,
                workingDir = config.workingDir,
                ideBridge = bridgeInfo,
                model = config.model.ifEmpty { null },
                timeoutSeconds = 600,
                onOutput = { chunk ->
                    val cleaned = AgentCli.cleanOutput(chunk)
                    if (cleaned.isNotBlank()) {
                        emitEvent(StreamEvent.Token(cleaned + "\n"))
                    }
                },
            )

            if (result.output.isNotBlank()) {
                emitEvent(StreamEvent.Token("\n" + AgentCli.cleanOutput(result.output) + "\n"))
            }
            if (result.error.isNotBlank()) {
                emitEvent(StreamEvent.Token("\n" + AgentCli.cleanOutput(result.error) + "\n"))
            }
            if (result.timedOut) {
                emitEvent(StreamEvent.Error("Request timed out"))
            }
            if (result.exitCode != 0) {
                emitEvent(StreamEvent.Error("Agent exited with code ${result.exitCode}"))
            }

            _state.update { it.copy(phase = SessionPhase.DONE, finishedAt = System.currentTimeMillis()) }
            emitEvent(StreamEvent.Done(
                finishReason = if (result.timedOut) "timeout" else "stop",
            ))
        }

        private fun emitEvent(event: StreamEvent) {
            eventChannel.trySend(event)
            _state.update { it.addEvent(event) }
            when (event) {
                is StreamEvent.Token -> {
                    if (_state.value.phase == SessionPhase.RUNNING) {
                        _state.update { it.copy(phase = SessionPhase.STREAMING) }
                    }
                }
                is StreamEvent.ToolCall -> {
                    _state.update { it.copy(phase = SessionPhase.WAITING_FOR_TOOL) }
                }
                is StreamEvent.Error -> {
                    _state.update { it.copy(phase = SessionPhase.ERROR, error = event.message) }
                }
                else -> {}
            }
        }
    }
}
