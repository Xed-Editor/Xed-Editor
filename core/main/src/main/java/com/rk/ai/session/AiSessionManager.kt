package com.rk.ai.session

import android.app.Activity
import android.util.Log
import com.rk.activities.main.MainViewModel
import com.rk.ai.AiConfig
import com.rk.ai.AgentCli
import com.rk.ai.IdeBridge
import com.rk.ai.agents.AiAgent
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.ai.resolvedConfiguredModelForAgent
import com.rk.ai.resolvedStoredModelForAgent
import com.rk.ai.setConfiguredModelForAgent
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.settings.Settings
import com.rk.terminal.setupTerminalFiles
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AiSessionState(
    val session: TerminalSession? = null,
    val cwd: String? = null,
    val currentAgent: AiAgent = AgentTypeRegistry.resolve(),
    val lastError: String? = null,
    val connectionStatus: AiSessionManager.ConnectionStatus = AiSessionManager.ConnectionStatus.Disconnected,
    val isRunning: Boolean = false,
)

object AiSessionManager {
    private const val TAG = "AiSessionManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AiSessionState())
    val state: StateFlow<AiSessionState> = _state.asStateFlow()

    var session: TerminalSession?
        get() = _state.value.session
        private set(value) { _state.update { it.copy(session = value, isRunning = value?.isRunning == true) } }

    var cwd: String?
        get() = _state.value.cwd
        private set(value) { _state.update { it.copy(cwd = value) } }

    var currentAgent: AiAgent
        get() = _state.value.currentAgent
        private set(value) { _state.update { it.copy(currentAgent = value) } }

    var lastError: String?
        get() = _state.value.lastError
        private set(value) { _state.update { it.copy(lastError = value) } }

    var connectionStatus: ConnectionStatus
        get() = _state.value.connectionStatus
        private set(value) { _state.update { it.copy(connectionStatus = value) } }

    private var sessionLaunchJob: Job? = null

    enum class ConnectionStatus {
        Disconnected, Connecting, Connected, Reconnecting, Error
    }

    fun initialize() {
        Log.d(TAG, "AiSessionManager initialized")
    }

    fun shutdown() {
        sessionLaunchJob?.cancel()
        stopSession()
        scope.cancel()
    }

    fun resolveAgent(type: String? = null): AiAgent = AgentTypeRegistry.resolve(type)

    fun availableAgents(): List<AiAgent> = AgentTypeRegistry.available()

    fun switchAgent(type: String) {
        val newAgent = resolveAgent(type)
        val previousAgent = currentAgent
        if (newAgent == previousAgent) {
            Settings.ai_agent = newAgent.name
            Settings.ai_model = resolvedConfiguredModelForAgent(newAgent).orEmpty()
            runCatching { IdeBridge.forceWriteAgentConfigs() }
            return
        }
        setConfiguredModelForAgent(previousAgent, resolvedConfiguredModelForAgent(previousAgent), syncActiveModel = false)
        stopSession()
        currentAgent = newAgent
        val incomingModel = resolvedStoredModelForAgent(newAgent).orEmpty()
        Settings.ai_agent = newAgent.name
        Settings.ai_model = incomingModel
        runCatching { IdeBridge.forceWriteAgentConfigs() }
        lastError = null
        connectionStatus = ConnectionStatus.Disconnected
    }

    fun canReuseFor(requestedCwd: String): Boolean {
        val s = session ?: return false
        if (!s.isRunning) return false
        val existingCwd = cwd ?: return false
        if (requestedCwd == existingCwd) return true
        if (existingCwd in AiConfig.commonReuseRoots) return true
        return requestedCwd.startsWith(existingCwd) || existingCwd.startsWith(requestedCwd)
    }

    fun updateCwd(newCwd: String) {
        cwd = newCwd
    }

    fun startSession(
        activity: Activity,
        viewModel: MainViewModel,
        workingDir: String? = null,
        extraArgs: List<String> = emptyList(),
    ) {
        sessionLaunchJob?.cancel()
        sessionLaunchJob = scope.launch {
            mutex.withLock {
                connectionStatus = ConnectionStatus.Connecting
                val dir = workingDir ?: cwd ?: return@launch
                try {
                    setupTerminalFiles()
                    val bridgeInfo = IdeBridge.ensureStarted(viewModel, dir)
                    val agentEnvBuilder = AgentEnvironmentBuilder

                    val tmpSubdir = "terminal/${currentAgent.name}-sheet"
                    val tmpDir = File(getTempDir(), tmpSubdir).apply { mkdirs() }
                    var args = currentAgent.buildArgs(
                        extraArgs = listOf("--approval-mode=default") + extraArgs,
                        workingDir = dir,
                        model = resolvedConfiguredModelForAgent(currentAgent).orEmpty()
                            .ifEmpty { currentAgent.defaultModel }
                    )

                    if (extraArgs.contains("--prompt-interactive")) {
                        val promptIndex = extraArgs.indexOf("--prompt-interactive") + 1
                        if (promptIndex < extraArgs.size && promptIndex > 0) {
                            val interactivePrompt = extraArgs[promptIndex]
                            args = currentAgent.buildArgs(
                                extraArgs = listOf("-p", interactivePrompt),
                                workingDir = dir,
                                model = resolvedConfiguredModelForAgent(currentAgent).orEmpty()
                                    .ifEmpty { currentAgent.defaultModel }
                            )
                        }
                    }

                    val command = arrayOf("/bin/bash", localBinDir().child(currentAgent.cliBinaryName).absolutePath, *args.toTypedArray())
                    val config = AgentEnvironmentConfig(
                        activity = activity,
                        workingDir = dir,
                        bridge = bridgeInfo ?: return@launch,
                        agent = currentAgent,
                        tmpSubdir = tmpSubdir,
                    )
                    val env = agentEnvBuilder.buildEnv(config)
                    val shell = "/system/bin/sh"
                    val ts = TerminalSession(shell, dir, command, env, Settings.terminal_scrollback_buffer,
                        com.rk.terminal.TerminalBackEnd()
                    ).also {
                        it.mSessionName = "${currentAgent.name}-agent"
                    }

                    session = ts
                    cwd = dir
                    connectionStatus = ConnectionStatus.Connected
                    lastError = null

                    if (bridgeInfo != null) {
                        agentEnvBuilder.writeBridgeEnvFile(tmpDir, File(dir, AiConfig.Discovery.xedIdeDir), bridgeInfo)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start session", e)
                    lastError = e.message
                    connectionStatus = ConnectionStatus.Error
                    session = null
                }
            }
        }
    }

    fun stopSession() {
        sessionLaunchJob?.cancel()
        session?.let { ts ->
            try { ts.finishIfRunning() } catch (_: Exception) {}
        }
        session = null
        cwd = null
        connectionStatus = ConnectionStatus.Disconnected
        lastError = null
    }

    suspend fun reconnect(activity: Activity, viewModel: MainViewModel): Boolean {
        stopSession()
        startSession(activity, viewModel)
        kotlinx.coroutines.delay(2000)
        val s = session
        return s != null && s.isRunning
    }

    fun write(text: String) {
        session?.write(text)
    }
}
