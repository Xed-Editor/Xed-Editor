@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.tools

import android.content.Context
import com.rk.ai.agent.agents.AgentRegistry
import com.rk.ai.models.Tool
import com.rk.ai.service.IdeService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface PluginToolProvider {
    val pluginId: String
    val pluginName: String
    fun getTools(): List<Tool>
}

class VibeCodingToolRegistry(
    private val ideService: IdeService,
    private val context: Context,
) {
    val agentRegistry = AgentRegistry(context, ideService)

    private val fileTools by lazy { VibeCodingFileTools(ideService) }
    private val editorTools by lazy { VibeCodingEditorTools(ideService) }
    private val searchTools by lazy { VibeCodingSearchTools(ideService) }
    private val lspTools by lazy { VibeCodingLspTools(ideService) }
    private val gitTools by lazy { VibeCodingGitTools(ideService) }
    private val terminalTools by lazy { VibeCodingTerminalTools(ideService) }
    private val projectTools by lazy { VibeCodingProjectTools(ideService) }
    private val systemTools by lazy { VibeCodingSystemTools(ideService, context) }
    private val suggestionTools by lazy { SuggestionTools(ideService) }
    private val diffTools by lazy { VibeCodingDiffTools(ideService) }
    private val webTools by lazy { VibeCodingWebTools(ideService) }
    private val githubTools by lazy { VibeCodingGitHubTools(ideService) }
    private val packageTools by lazy { VibeCodingPackageTools(ideService) }

    var onAgentResult: ((String, com.rk.ai.agent.agents.AgentResult) -> Unit)? = null

    private val agentListTool by lazy { agentRegistry.getAgentListTool() }
    private val agentDelegateTool by lazy { agentRegistry.getDelegateTool { name, result ->
        onAgentResult?.invoke(name, result)
    }}

    private val pluginProviders = mutableListOf<PluginToolProvider>()

    private val _coreTools: List<Tool> by lazy {
        fileTools.all +
            editorTools.all +
            searchTools.all +
            lspTools.all +
            gitTools.all +
            terminalTools.all +
            projectTools.all +
            systemTools.all +
            diffTools.all +
            webTools.all +
            githubTools.all +
            packageTools.all +
            suggestionTools.all +
            listOf(agentListTool, agentDelegateTool)
    }

    val allTools: List<Tool> get() {
        val pluginTools = pluginProviders.flatMap { it.getTools() }
        return _coreTools + pluginTools
    }

    fun withMcpTools(mcpTools: List<Tool>): List<Tool> {
        val pluginTools = pluginProviders.flatMap { it.getTools() }
        return _coreTools + mcpTools + pluginTools
    }

    fun registerPluginProvider(provider: PluginToolProvider) {
        pluginProviders.removeAll { it.pluginId == provider.pluginId }
        pluginProviders.add(provider)
    }

    fun unregisterPluginProvider(pluginId: String) {
        pluginProviders.removeAll { it.pluginId == pluginId }
    }

    fun getRegisteredPlugins(): List<PluginToolProvider> = pluginProviders.toList()
}
