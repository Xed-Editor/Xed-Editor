package com.rk.ai.session

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.activities.main.MainViewModel
import com.rk.ai.AiConfig
import com.rk.ai.GeminiCli
import com.rk.ai.IdeBridge
import com.rk.ai.agents.AiAgent
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.ai.agents.GeminiAgent
import com.rk.ai.bridge.server.IdeBridgeServer
import com.rk.ai.service.IdeService
import com.rk.ai.service.IdeServiceImpl
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
    var bridgeServer: IdeBridgeServer? = null
    var ideService: IdeService? = null
    var currentAgent by mutableStateOf<AiAgent>(GeminiAgent)

    fun resolveAgent(type: String? = null): AiAgent = AgentTypeRegistry.resolve(type)

    fun availableAgents(): List<AiAgent> = AgentTypeRegistry.available()

    fun switchAgent(type: String) {
        val newAgent = resolveAgent(type)
        if (newAgent != currentAgent) {
            stopSession()
            currentAgent = newAgent
            Settings.ai_agent = type
        }
    }

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("AiSessionManager", msg)
    }

    fun canReuseFor(requestedCwd: String): Boolean {
        if (session == null || !session!!.isRunning) return false
        val existingCwd = cwd ?: return false
        if (requestedCwd == existingCwd) return true
        if (existingCwd in AiConfig.commonReuseRoots) return true
        return requestedCwd.startsWith("$existingCwd/")
    }

    suspend fun startSession(
        activity: Activity,
        viewModel: MainViewModel,
        workingDir: String,
        extraArgs: List<String> = emptyList(),
        agentType: String? = null,
    ): TerminalSession {
        currentAgent = resolveAgent(agentType)
        d("startSession agent=${currentAgent.name} workingDir=$workingDir")

        val projectConfig = com.rk.ai.ProjectConfigLoader.loadForWorkspace(workingDir)
        if (projectConfig != null) {
            com.rk.ai.ProjectConfigLoader.applyConfig(projectConfig)
            currentAgent = resolveAgent()
            d("project config applied: ${com.rk.ai.ProjectConfigLoader.describeConfig(projectConfig)}")
        }

        stopSession()

        return withContext(Dispatchers.IO) {
            IdeBridge.ensureStarted(viewModel)
            IdeBridge.setWorkspacePath(workingDir)
            val bridgeInfo = IdeBridge.getBridgeInfo()!!

            withContext(Dispatchers.Main) {
                ideService = IdeServiceImpl(viewModel)
                try {
                    val newSession = createAgentSession(
                        activity = activity,
                        agent = currentAgent,
                        bridge = bridgeInfo,
                        workingDir = workingDir,
                        extraArgs = extraArgs,
                    )
                    session = newSession
                    cwd = workingDir
                    newSession
                } catch (e: Exception) {
                    d("Failed to create session: ${e.message}")
                    session = null
                    cwd = null
                    throw e
                }
            }
        }
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
        IdeBridge.stop()
        ideService = null
    }

    suspend fun runHeadless(prompt: String, workingDir: String, timeoutSeconds: Long = 60): String {
        val agent = currentAgent
        val bridgeInfo = IdeBridge.getBridgeInfo()
        return when (agent.name) {
            "opencode" -> {
                val result = com.rk.ai.GeminiCli.agent(
                    prompt = prompt,
                    workingDir = workingDir,
                    ideBridge = bridgeInfo,
                    timeoutSeconds = timeoutSeconds,
                )
                GeminiCli.stripCodeFences(result.output)
            }
            else -> {
                val result = GeminiCli.agent(
                    prompt = prompt,
                    workingDir = workingDir,
                    ideBridge = bridgeInfo,
                    timeoutSeconds = timeoutSeconds,
                )
                GeminiCli.stripCodeFences(result.output)
            }
        }
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
            addAll(agent.buildArgs(extraArgs, workingDir, Settings.ai_model.takeIf { it.isNotBlank() }))
        }
        return "/system/bin/sh" to arrayOf("sh", *command.toTypedArray())
    }
}