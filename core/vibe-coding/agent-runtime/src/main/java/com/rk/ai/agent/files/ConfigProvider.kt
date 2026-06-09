package com.rk.ai.agent.files

import android.content.Context
import android.util.Log
import com.rk.ai.agent.VibeCodingError
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus
import com.rk.ai.models.McpServerConfig
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.persistence.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UnifiedConfig(
    val permissionRules: List<PermissionRuleDef> = emptyList(),
    val mcpServers: List<McpServerConfigDef> = emptyList(),
    val modelConfig: ModelConfigDef? = null,
    val instructions: String? = null,
    val toolOverrides: Map<String, Boolean> = emptyMap(),
    val skills: Map<String, Boolean> = emptyMap(),
) {
    fun isToolEnabled(toolName: String): Boolean = toolOverrides[toolName] ?: true
}

@Serializable
data class PermissionRuleDef(
    val toolPattern: String = "*",
    val argPattern: String = "*",
    val action: String = "ask",
    val description: String = "",
)

@Serializable
data class McpServerConfigDef(
    val name: String = "",
    val url: String? = null,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val disabled: Boolean = false,
)

@Serializable
data class ModelConfigDef(
    val provider: String? = null,
    val model: String? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
)

class ConfigProvider(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val workspacePath: () -> String,
    scope: CoroutineScope? = null,
) {
    private val tag = "ConfigProvider"
    private val providerScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _unifiedConfig = MutableStateFlow(UnifiedConfig())
    val unifiedConfig: StateFlow<UnifiedConfig> = _unifiedConfig.asStateFlow()

    private val _lastError = MutableStateFlow<VibeCodingError?>(null)
    val lastError: StateFlow<VibeCodingError?> = _lastError.asStateFlow()

    init {
        providerScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                reload(settings)
            }
        }
    }

    fun reload() {
        reload(settingsStore.settingsFlow.value)
    }

    private fun reload(settings: Settings) {
        try {
            val workspace = workspacePath()
            val xedConfig = if (workspace.isNotBlank()) XedConfigLoader.loadConfig(workspace) else XedConfig()

            val merged = mergeConfigs(settings, xedConfig)
            _unifiedConfig.value = merged
            _lastError.value = null
        } catch (e: Exception) {
            Log.w(tag, "Config reload failed", e)
            _lastError.value = VibeCodingError.ConfigError.ParseError("config", e)
        }
    }

    fun refreshConfig() {
        settingsStore.settingsFlow.value.let { settings ->
            reload(settings)
        }
    }

    private fun mergeConfigs(settings: Settings, xed: XedConfig): UnifiedConfig {
        val permissionRules = buildList {
            addAll(settingsToPermissionRules(settings))
            addAll(xed.permission.map { it.toPermissionRuleDef() })
        }

        val mcpServers = buildList {
            addAll(settings.mcpServers.map { it.toMcpConfigDef() })
            addAll(xed.mcp.map { it.toMcpConfigDef() }.filter { !it.disabled })
        }

        val toolOverrides = xed.tools
        val skills = settings.assistants
            .firstOrNull { it.id == settings.assistantId }
            ?.enabledSkills?.associateWith { true } ?: emptyMap()

        UnifiedConfig(
            permissionRules = permissionRules,
            mcpServers = mcpServers,
            modelConfig = xed.model?.let { ModelConfigDef(provider = it.provider, model = it.model) },
            instructions = xed.instructions,
            toolOverrides = toolOverrides,
            skills = skills,
        )
    }

    private fun settingsToPermissionRules(settings: Settings): List<PermissionRuleDef> {
        return emptyList()
    }
}

private fun com.rk.ai.models.McpServerConfig.toMcpConfigDef(): McpServerConfigDef {
    return when (this) {
        is McpServerConfig.SseTransportServer -> McpServerConfigDef(
            name = commonOptions?.name ?: url,
            url = url,
        )
        is McpServerConfig.StreamableHTTPServer -> McpServerConfigDef(
            name = commonOptions?.name ?: url,
            url = url,
        )
    }
}

private fun XedPermissionRule.toPermissionRuleDef(): PermissionRuleDef {
    return PermissionRuleDef(
        toolPattern = tool,
        argPattern = arg,
        action = action,
        description = description,
    )
}

private fun McpServerConfigDef.toMcpServerConfig(): McpServerConfig? {
    url?.let { return McpServerConfig.SseTransportServer(url = it) }
    return null
}
