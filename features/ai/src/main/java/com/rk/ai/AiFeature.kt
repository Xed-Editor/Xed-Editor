package com.rk.ai

import android.app.Application
import com.rk.ai.api.AiExtensions
import com.rk.ai.command.OpenAiChatCommand
import com.rk.ai.icons.AiBrain
import com.rk.ai.icons.SparklesBig
import com.rk.ai.settings.AiMemoryScreen
import com.rk.ai.settings.AiSettingsScreen
import com.rk.ai.tab.AiTab
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.feature.FeatureToggle
import com.rk.icons.Icon
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry

class AiFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.ai_feature_label.getString(),
            key = "feature_ai",
            default = true,
            icon = Icon.VectorIcon(AiBrain),
        )

    private var settingsCategory: SettingsCategory? = null
    private val settingsRoutes = mutableListOf<DynamicRoute>()
    private val aiChatCommand = OpenAiChatCommand()

    override fun init(application: Application) {
        AiExtensions.installBuiltins()
        AiTab.register()
        registerSettings()
        registerCommands()
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

        settingsRoutes +=
            DynamicRoute(AI_SETTINGS_ROUTE) { navController, _ ->
                AiSettingsScreen(onOpenMemory = { navController.navigate(AI_MEMORY_ROUTE) })
            }
        settingsRoutes += DynamicRoute(AI_MEMORY_ROUTE) { _, _ -> AiMemoryScreen() }
        settingsRoutes.forEach { SettingsRegistry.registerRoute(it) }
    }

    override fun dispose(application: Application) {
        AiTab.unregister()
        ToolbarConfiguration.removeGlobalToolbarCommand(aiChatCommand)
        CommandProvider.unregisterCommand(aiChatCommand)

        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        settingsCategory = null
        settingsRoutes.forEach { SettingsRegistry.unregisterRoute(it) }
        settingsRoutes.clear()
    }

    private companion object {
        private const val AI_SETTINGS_ROUTE = "ai_settings"
        private const val AI_MEMORY_ROUTE = "ai_memory"
    }
}
