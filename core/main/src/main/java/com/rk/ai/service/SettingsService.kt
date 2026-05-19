package com.rk.ai.service

import com.google.gson.JsonObject
import com.rk.ai.resolvedConfiguredModelForAgent
import com.rk.ai.setConfiguredModelForAgent
import com.rk.ai.session.AiSessionManager
import com.rk.settings.Settings

class SettingsService {

    fun getSetting(key: String): String? {
        return when (key) {
            "ai_agent" -> Settings.ai_agent
            "ai_model" -> resolvedConfiguredModelForAgent(AiSessionManager.resolveAgent(Settings.ai_agent))
            "ai_model_gemini" -> Settings.ai_model_gemini.takeIf { it.isNotBlank() }
            "ai_model_opencode" -> Settings.ai_model_opencode.takeIf { it.isNotBlank() }
            "ai_api_key" -> if (Settings.ai_api_key.isNotBlank()) "(set)" else null
            "ai_profiles_json" -> Settings.ai_profiles_json.takeIf { it.isNotBlank() }
            "ai_auto_apply" -> Settings.ai_auto_apply.toString()
            "ai_inline_completion" -> Settings.ai_inline_completion.toString()
            "ai_completion_model" -> Settings.ai_completion_model.takeIf { it.isNotBlank() }
            "ai_completion_url" -> Settings.ai_completion_url.takeIf { it.isNotBlank() }
            "terminal_scrollback_buffer" -> Settings.terminal_scrollback_buffer.toString()
            "sandbox" -> Settings.sandbox.toString()
            "seccomp" -> Settings.seccomp.toString()
            "git_username" -> Settings.git_username.takeIf { it.isNotBlank() }
            "always_index_projects" -> Settings.always_index_projects.toString()
            "language" -> Settings.current_lang
            else -> null
        }
    }

    fun setSetting(key: String, value: String) {
        when (key) {
            "ai_agent" -> {
                AiSessionManager.switchAgent(AiSessionManager.resolveAgent(value).name)
                return
            }
            "ai_model" -> {
                setConfiguredModelForAgent(
                    agent = AiSessionManager.resolveAgent(Settings.ai_agent),
                    model = value,
                    syncActiveModel = true,
                )
                return
            }
            "ai_model_gemini" -> {
                setConfiguredModelForAgent(
                    agent = AiSessionManager.resolveAgent("gemini"),
                    model = value,
                    syncActiveModel = (Settings.ai_agent == "gemini"),
                )
                return
            }
            "ai_model_opencode" -> {
                setConfiguredModelForAgent(
                    agent = AiSessionManager.resolveAgent("opencode"),
                    model = value,
                    syncActiveModel = (Settings.ai_agent == "opencode"),
                )
                return
            }
        }

        val prefs = com.rk.settings.Preference.getAll()
        val existing = prefs[key]
        when (existing) {
            is Boolean -> com.rk.settings.Preference.setBoolean(key, value.toBooleanStrictOrNull() ?: existing)
            is Int -> com.rk.settings.Preference.setInt(key, value.toIntOrNull() ?: existing)
            is Long -> com.rk.settings.Preference.setLong(key, value.toLongOrNull() ?: existing)
            is Float -> com.rk.settings.Preference.setFloat(key, value.toFloatOrNull() ?: existing)
            else -> com.rk.settings.Preference.setString(key, value)
        }
    }

    fun getAllSettings(): JsonObject {
        return JsonObject().apply {
            addProperty("ai_agent", Settings.ai_agent)
            addProperty("ai_model", resolvedConfiguredModelForAgent(AiSessionManager.resolveAgent(Settings.ai_agent)).orEmpty())
            addProperty("ai_model_gemini", Settings.ai_model_gemini)
            addProperty("ai_model_opencode", Settings.ai_model_opencode)
            addProperty("ai_api_key", if (Settings.ai_api_key.isNotBlank()) "(set)" else "")
            addProperty("ai_auto_apply", Settings.ai_auto_apply)
            addProperty("ai_inline_completion", Settings.ai_inline_completion)
            addProperty("ai_completion_model", Settings.ai_completion_model)
            addProperty("ai_completion_url", Settings.ai_completion_url)
            addProperty("terminal_scrollback_buffer", Settings.terminal_scrollback_buffer)
            addProperty("sandbox", Settings.sandbox)
            addProperty("seccomp", Settings.seccomp)
            addProperty("always_index_projects", Settings.always_index_projects)
            addProperty("language", Settings.current_lang)
        }
    }
}
