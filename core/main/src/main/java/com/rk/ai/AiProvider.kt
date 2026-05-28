package com.rk.ai

import android.app.Activity
import androidx.compose.runtime.MutableState
import com.rk.activities.main.MainViewModel

object AiProvider {
    var ideBridge: IdeBridgeProvider? = null
    var sessionManager: SessionManagerProvider? = null
    var completionEngine: CompletionEngineProvider? = null
    var agentEnvBuilder: AgentEnvBuilderProvider? = null

    data class BridgeInfo(val port: Int, val token: String, val host: String = "127.0.0.1")
    data class AgentInfo(
        val name: String,
        val displayName: String,
        val shellScriptName: String,
        val cliBinaryName: String,
        val buildArgs: (List<String>, String) -> List<String>
    )
    data class CompletionResult(val text: String, val line: Int, val column: Int)

    interface IdeBridgeProvider {
        fun ensureStarted(viewModel: MainViewModel, workspacePath: String?): BridgeInfo?
        fun isRunning(): Boolean
        fun healthCheck(): Boolean
        fun getBridgeInfo(): BridgeInfo?
        fun primaryWorkspacePath(): String
        fun connectedClients(): Int
        fun setWorkspacePath(path: String)
    }

    interface SessionManagerProvider {
        val sessionState: MutableState<Any?>
        val cwd: String?
        val currentAgent: AgentInfo
        fun availableAgents(): List<AgentInfo>
        fun resolveAgent(type: String?): AgentInfo
        fun switchAgent(type: String)
        fun canReuseFor(requestedCwd: String): Boolean
        suspend fun startSession(activity: Activity, viewModel: MainViewModel, workingDir: String, extraArgs: List<String>)
        fun stopSession()
        suspend fun runHeadless(prompt: String, workingDir: String, timeoutSeconds: Long = 60): String
    }

    interface CompletionEngineProvider {
        suspend fun getInlineCompletion(
            filePath: String,
            content: String,
            cursorLine: Int,
            cursorColumn: Int,
            language: String
        ): CompletionResult?
    }

    interface AgentEnvBuilderProvider {
        fun buildMinimalBridgeEnv(bridge: BridgeInfo, workingDir: String): Array<String>
    }
}
