package com.rk.ai.session

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.activities.main.MainViewModel
import com.rk.ai.AiConfig
import com.rk.ai.AgentCli
import com.rk.ai.IdeBridge
import com.rk.ai.resolvedConfiguredModelForAgent
import com.rk.ai.resolvedStoredModelForAgent
import com.rk.ai.setConfiguredModelForAgent
import com.rk.ai.agents.AiAgent
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.settings.Settings
import com.rk.terminal.setupTerminalFiles
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiSessionManager {
    var session by mutableStateOf<TerminalSession?>(null)
    var cwd by mutableStateOf<String?>(null)
    var currentAgent by mutableStateOf<AiAgent>(AgentTypeRegistry.resolve())
    var lastError by mutableStateOf<String?>(null)
    var connectionStatus by mutableStateOf<ConnectionStatus>(ConnectionStatus.Disconnected)

    enum class ConnectionStatus {
        Disconnected, Connecting, Connected, Reconnecting, Error
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

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("AiSessionManager", msg)
    }

    fun canReuseFor(requestedCwd: String): Boolean {
        val s = session ?: return false
        if (!s.isRunning) return false
        val existingCwd = cwd ?: return false
        if (requestedCwd == existingCwd) return true
        if (existingCwd in AiConfig.commonReuseRoots) return true
        return requestedCwd.startsWith("$existingCwd/")
    }

    fun updateCwd(newCwd: String) {
        val s = session ?: return
        if (!s.isRunning) return
        val oldCwd = cwd
        cwd = newCwd
        IdeBridge.setWorkspacePath(newCwd)
        if (newCwd != oldCwd) {
            try {
                s.write("cd \"$newCwd\"\r")
                d("Session cwd updated: $oldCwd -> $newCwd")
            } catch (e: Exception) {
                d("Failed to cd session: ${e.message}")
            }
        }
    }

    suspend fun startSession(
        activity: Activity,
        viewModel: MainViewModel,
        workingDir: String,
        extraArgs: List<String> = emptyList(),
        agentType: String? = null,
        maxRetries: Int = 2,
    ): TerminalSession {
        val previousAgentName = Settings.ai_agent
        currentAgent = resolveAgent(agentType)
        Settings.ai_agent = currentAgent.name
        val initialModel = if (currentAgent.name == previousAgentName) {
            resolvedConfiguredModelForAgent(currentAgent).orEmpty()
        } else {
            resolvedStoredModelForAgent(currentAgent).orEmpty()
        }
        Settings.ai_model = initialModel
        d("startSession agent=${currentAgent.name} workingDir=$workingDir")
        lastError = null
        connectionStatus = ConnectionStatus.Connecting

        val projectConfig = com.rk.ai.ProjectConfigLoader.loadForWorkspace(workingDir)
        if (projectConfig != null) {
            com.rk.ai.ProjectConfigLoader.applyConfig(projectConfig)
            currentAgent = resolveAgent()
            Settings.ai_model = resolvedConfiguredModelForAgent(currentAgent).orEmpty()
            d("project config applied: ${com.rk.ai.ProjectConfigLoader.describeConfig(projectConfig)}")
        }

        stopSession()

        var lastException: Exception? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                return withContext(Dispatchers.IO) {
                    val bridgeInfo = IdeBridge.ensureStarted(viewModel, workingDir)
                    if (bridgeInfo == null) {
                        lastError = IdeBridge.getLastError() ?: "Failed to start bridge"
                        connectionStatus = ConnectionStatus.Error
                        throw Exception(lastError)
                    }

                    IdeBridge.setWorkspacePath(workingDir)

                    val (mcpOk, mcpStatus) = IdeBridge.checkMcpConnection()
                    d("MCP connection check: $mcpOk - $mcpStatus")
                    if (!mcpOk) {
                        lastError = "Bridge MCP check failed: $mcpStatus"
                        connectionStatus = ConnectionStatus.Error
                        throw Exception(lastError)
                    }

                    val (toolsOk, toolsStatus) = IdeBridge.verifyMcpToolsAvailable()
                    d("MCP tools check: $toolsOk - $toolsStatus")
                    if (!toolsOk) {
                        lastError = "Bridge MCP tools unavailable: $toolsStatus"
                        connectionStatus = ConnectionStatus.Error
                        throw Exception(lastError)
                    }

                    val newSession = createAgentSession(
                        activity = activity,
                        agent = currentAgent,
                        bridge = bridgeInfo,
                        workingDir = workingDir,
                        extraArgs = extraArgs,
                    )
                    withContext(Dispatchers.Main) {
                        session = newSession
                        cwd = workingDir
                        connectionStatus = ConnectionStatus.Connected
                        lastError = null
                    }
                    d("Session started successfully")
                    newSession
                }
            } catch (e: Exception) {
                lastException = e
                d("Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries) {
                    connectionStatus = ConnectionStatus.Reconnecting
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                    }
                    IdeBridge.stop()
                } else {
                    lastError = e.message
                    connectionStatus = ConnectionStatus.Error
                }
            }
        }
        throw lastException ?: Exception("Failed to start session after $maxRetries retries")
    }

    fun validateSession(): Boolean {
        val s = session ?: return false
        if (!s.isRunning) {
            connectionStatus = ConnectionStatus.Disconnected
            return false
        }
        if (!IdeBridge.isRunning()) {
            connectionStatus = ConnectionStatus.Error
            lastError = "IDE bridge not running"
            return false
        }
        val (ok, status) = IdeBridge.checkMcpConnection()
        if (!ok) {
            connectionStatus = ConnectionStatus.Error
            lastError = status
            return false
        }
        return true
    }

    fun stopSession() {
        d("stopSession")
        try {
            session?.finishIfRunning()
        } catch (e: Exception) {
            d("Error stopping session: ${e.message}")
        }
        session = null
        cwd = null
        connectionStatus = ConnectionStatus.Disconnected
        lastError = null
    }

    suspend fun reconnect(activity: Activity, viewModel: MainViewModel): Boolean {
        val workingDir = cwd ?: run {
            lastError = "No working directory for reconnection"
            return false
        }
        connectionStatus = ConnectionStatus.Reconnecting
        return try {
            startSession(activity, viewModel, workingDir)
            true
        } catch (e: Exception) {
            lastError = "Reconnection failed: ${e.message}"
            connectionStatus = ConnectionStatus.Error
            false
        }
    }

    suspend fun runHeadless(prompt: String, workingDir: String, timeoutSeconds: Long = 60): String {
        val bridgeInfo = IdeBridge.getBridgeInfo()
        if (bridgeInfo == null) {
            lastError = "Bridge not available"
            throw Exception("IDE bridge not running")
        }
        val result = AgentCli.runAgent(
            prompt = prompt,
            agent = currentAgent,
            workingDir = workingDir,
            ideBridge = bridgeInfo,
            model = resolvedConfiguredModelForAgent(currentAgent),
            timeoutSeconds = timeoutSeconds,
        )
        return AgentCli.stripCodeFences(result.output)
    }

    private fun createAgentSession(
        activity: Activity,
        agent: AiAgent,
        bridge: IdeBridge.Info,
        workingDir: String,
        extraArgs: List<String> = emptyList(),
    ): TerminalSession {
        setupTerminalFiles()
        val tmpDir = File(getTempDir(), "terminal/${agent.name}-sheet").apply { mkdirs() }
        val xedDir = if (workingDir.isNotBlank() && File(workingDir).exists()) {
            File(workingDir, ".xed").also { it.mkdirs() }
        } else null
        AgentEnvironmentBuilder.writeBridgeEnvFile(tmpDir, xedDir, bridge)
        val (shell, args) = agentSheetProcessArgs(agent, extraArgs, xedDir, workingDir)
        val env = AgentEnvironmentBuilder.buildEnv(
            AgentEnvironmentConfig(
                activity = activity,
                workingDir = workingDir,
                bridge = bridge,
                agent = agent,
                tmpSubdir = "${agent.name}-sheet",
            )
        )
        return TerminalSession(
            shell,
            workingDir,
            args,
            env,
            Settings.terminal_scrollback_buffer,
            com.rk.terminal.TerminalBackEnd(),
        ).also { it.mSessionName = "${agent.name}-sheet" }
    }

    private fun agentSheetProcessArgs(
        agent: AiAgent,
        extraArgs: List<String>,
        xedDir: File?,
        workingDir: String,
    ): Pair<String, Array<String>> {
        val sandbox = localBinDir().child("sandbox").absolutePath
        val launcher = localBinDir().child(agent.shellScriptName).absolutePath

        val wrapperDir = xedDir ?: File(getTempDir(), "terminal/${agent.name}-sheet").also { it.mkdirs() }
        val envFile = File(wrapperDir, AiConfig.Discovery.ideEnvFile)
        val wrapperScript = File(wrapperDir, AiConfig.Discovery.launcherScriptFile)
        if (!wrapperScript.exists()) {
            wrapperScript.writeText(
                buildString {
                    appendLine("#!/bin/bash")
                    appendLine("# Auto-generated launcher wrapper - sources IDE bridge env")
                    if (envFile.exists()) {
                        appendLine("source ${envFile.absolutePath}")
                    }
                    appendLine("exec $launcher \"\$@\"")
                }
            )
            wrapperScript.setExecutable(true)
        }

        val command = buildList {
            add(sandbox)
            add("/bin/bash")
            add(wrapperScript.absolutePath)
            addAll(agent.buildArgs(extraArgs, workingDir, resolvedConfiguredModelForAgent(agent)))
        }
        return "/system/bin/sh" to arrayOf("sh", *command.toTypedArray())
    }
}