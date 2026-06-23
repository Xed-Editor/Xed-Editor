package com.rk.ai

import androidx.compose.runtime.mutableStateOf
import com.rk.ai.agents.AiAgent
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.ai.service.IdeServiceImpl
import com.rk.ai.session.AgentEnvironmentBuilder
import com.rk.ai.session.AiSessionManager

object AiRegistrar {
    fun init() {
        AiProvider.sessionManager = SessionManagerProviderImpl()
        AiProvider.completionEngine = CompletionEngineProviderImpl()
        AiProvider.ideBridge = IdeBridgeProviderImpl()
        AiProvider.agentEnvBuilder = AgentEnvBuilderProviderImpl()
        AiProvider.ideServiceFactory = IdeServiceFactoryProviderImpl()
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
            viewModel: com.rk.activities.main.MainViewModel?,
        ): String = AiSessionManager.runHeadless(prompt, workingDir, timeoutSeconds, viewModel)

        private fun toAgentInfo(agent: AiAgent): AiProvider.AgentInfo =
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
        override fun availableTools(): Int = IdeBridge.availableTools()
        override fun setWorkspacePath(path: String) = IdeBridge.setWorkspacePath(path)
        override fun refreshExternalMcp() = IdeBridge.refreshExternalMcp()
        override fun getExternalMcpStatus(): String? {
            val status = IdeBridge.getExternalMcpStatus() ?: return null
            return com.google.gson.GsonBuilder().create().toJson(status)
        }
        override fun setOnMcpServersConfigChanged(callback: ((String) -> Unit)?) {
            IdeBridge.onMcpServersConfigChanged = callback
        }
    }

    private class IdeServiceFactoryProviderImpl : AiProvider.IdeServiceFactoryProvider {
        override fun create(viewModel: com.rk.activities.main.MainViewModel) =
            IdeServiceImpl(viewModel)
    }

    private class AgentEnvBuilderProviderImpl : AiProvider.AgentEnvBuilderProvider {
        override fun buildMinimalBridgeEnv(
            bridge: AiProvider.BridgeInfo,
            workingDir: String,
        ): List<String> = AgentEnvironmentBuilder.buildMinimalBridgeEnv(
            IdeBridge.Info(port = bridge.port, token = bridge.token, host = bridge.host),
            workingDir,
        )
    }
}
