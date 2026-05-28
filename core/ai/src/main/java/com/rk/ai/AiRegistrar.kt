package com.rk.ai

import androidx.compose.runtime.mutableStateOf
import com.rk.ai.agents.AiAgent
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.ai.session.AiSessionManager

object AiRegistrar {
    fun init() {
        AiProvider.sessionManager = SessionManagerProviderImpl()
        AiProvider.completionEngine = CompletionEngineProviderImpl()
        AiProvider.ideBridge = IdeBridgeProviderImpl()
        AiProvider.agentEnvBuilder = AgentEnvBuilderProviderImpl()
    }

    private class SessionManagerProviderImpl : AiProvider.SessionManagerProvider {
        override val sessionState = mutableStateOf<Any?>(null)
        override val cwd: String? get() = AiSessionManager.cwd
        override val currentAgent: AiProvider.AgentInfo
            get() = AiSessionManager.currentAgent.let { toAgentInfo(it) }

        override fun availableAgents(): List<AiProvider.AgentInfo> =
            AgentTypeRegistry.available().map { toAgentInfo(it) }

        override fun resolveAgent(type: String?): AiProvider.AgentInfo =
            toAgentInfo(AgentTypeRegistry.resolve(type))

        override fun switchAgent(type: String) = AiSessionManager.switchAgent(type)

        override fun canReuseFor(requestedCwd: String): Boolean =
            AiSessionManager.canReuseFor(requestedCwd)

        override suspend fun startSession(
            activity: android.app.Activity,
            viewModel: com.rk.activities.main.MainViewModel,
            workingDir: String,
            extraArgs: List<String>,
        ) {
            AiSessionManager.startSession(activity, viewModel, workingDir, extraArgs)
            sessionState.value = AiSessionManager.session
        }

        override fun stopSession() {
            AiSessionManager.stopSession()
            sessionState.value = AiSessionManager.session
        }

        override suspend fun runHeadless(
            prompt: String,
            workingDir: String,
            timeoutSeconds: Long,
        ): String = AiSessionManager.runHeadless(prompt, workingDir, timeoutSeconds)

        private fun toAgentInfo(agent: agents.AiAgent): AiProvider.AgentInfo =
            AiProvider.AgentInfo(
                name = agent.name,
                displayName = agent.displayName,
                shellScriptName = agent.shellScriptName,
                cliBinaryName = agent.cliBinaryName,
                buildArgs = { extraArgs, wd -> agent.buildArgs(extraArgs, wd) },
            )
    }

    private class CompletionEngineProviderImpl : AiProvider.CompletionEngineProvider {
        override suspend fun getInlineCompletion(
            filePath: String,
            content: String,
            cursorLine: Int,
            cursorColumn: Int,
            language: String,
        ): AiProvider.CompletionResult? {
            return AiCompletionEngine.getInlineCompletion(
                filePath = filePath,
                content = content,
                cursorLine = cursorLine,
                cursorColumn = cursorColumn,
                language = language,
            )?.let {
                AiProvider.CompletionResult(text = it.text, line = it.line, column = it.column)
            }
        }
    }

    private class IdeBridgeProviderImpl : AiProvider.IdeBridgeProvider {
        override fun ensureStarted(
            viewModel: com.rk.activities.main.MainViewModel,
            workspacePath: String?,
        ): AiProvider.BridgeInfo? =
            IdeBridge.ensureStarted(viewModel, workspacePath)?.let {
                AiProvider.BridgeInfo(port = it.port, token = it.token, host = it.host)
            }

        override fun isRunning(): Boolean = IdeBridge.isRunning()
        override fun healthCheck(): Boolean = IdeBridge.healthCheck()
        override fun getBridgeInfo(): AiProvider.BridgeInfo? =
            IdeBridge.getBridgeInfo()?.let {
                AiProvider.BridgeInfo(port = it.port, token = it.token, host = it.host)
            }

        override fun primaryWorkspacePath(): String = IdeBridge.primaryWorkspacePath()
        override fun connectedClients(): Int = IdeBridge.connectedClients()
        override fun setWorkspacePath(path: String) = IdeBridge.setWorkspacePath(path)
    }

    private class AgentEnvBuilderProviderImpl : AiProvider.AgentEnvBuilderProvider {
        override fun buildMinimalBridgeEnv(
            bridge: AiProvider.BridgeInfo,
            workingDir: String,
        ): Array<String> = session.AgentEnvironmentBuilder.buildMinimalBridgeEnv(
            IdeBridge.Info(port = bridge.port, token = bridge.token, host = bridge.host),
            workingDir,
        )
    }
}
