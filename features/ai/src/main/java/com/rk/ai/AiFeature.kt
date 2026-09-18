package com.rk.ai

import android.app.Application
import android.util.Log
import com.rk.ai.command.OpenAiChatCommand
import com.rk.ai.icons.AiBrain
import com.rk.ai.icons.SparklesBig
import com.rk.ai.settings.AiSettingsScreen
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.feature.FeatureToggle
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AiFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings
                .ai_feature_label.getString(),
            key = "feature_ai",
            default = true,
            icon = Icon.VectorIcon(AiBrain),
        )

    private var scope: CoroutineScope? = null
    private var client: Client? = null
    private var serverSession: ServerSession? = null
    private var settingsCategory: SettingsCategory? = null
    private var settingsRoute: DynamicRoute? = null
    private val aiChatCommand = OpenAiChatCommand()

    val tools = mutableListOf<RegisteredTool>()

    override fun init(application: Application) {
        registerSettings()
        registerCommands()

        val featureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = featureScope
    }

    private fun registerCommands() {
        CommandProvider.registerCommand(aiChatCommand)
        ToolbarConfiguration.addGlobalToolbarCommand(aiChatCommand, 0)
    }

    private fun registerSettings() {
        settingsCategory =
            SettingsCategory(
                    label = "AI",
                    description = "Chat model, API key and tool permissions",
                    icon = Icon.VectorIcon(SparklesBig),
                    route = AI_SETTINGS_ROUTE,
                )
                .also { SettingsRegistry.registerCategory(it) }

        settingsRoute =
            DynamicRoute(AI_SETTINGS_ROUTE) { _, _ -> AiSettingsScreen() }
                .also { SettingsRegistry.registerRoute(it) }
    }

    override fun dispose(application: Application) {
        ToolbarConfiguration.removeGlobalToolbarCommand(aiChatCommand)
        CommandProvider.unregisterCommand(aiChatCommand)

        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        settingsCategory = null
        settingsRoute?.let { SettingsRegistry.unregisterRoute(it) }
        settingsRoute = null

        val openClient = client
        val openSession = serverSession
        client = null
        serverSession = null

        scope?.cancel()
        scope = null

        if (openClient != null || openSession != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { openClient?.close() }
                    .onFailure { Log.w(TAG, "Failed to close AI client", it) }
                runCatching { openSession?.close() }
                    .onFailure { Log.w(TAG, "Failed to close AI server session", it) }
            }
        }
    }

    private companion object {
        private const val TAG = "AiFeature"
        private const val AI_SETTINGS_ROUTE = "ai_settings"
    }
}
