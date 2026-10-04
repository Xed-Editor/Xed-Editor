package com.rk.ai.settings

import com.rk.settings.CachedPreference
import com.rk.settings.Preference
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object AiSettings {
    /** Must match the DeepSeek built-in provider id. */
    const val DEFAULT_PROVIDER_ID = "deepseek"

    var providerId by CachedPreference("ai_provider_id", DEFAULT_PROVIDER_ID)

    var modelId by CachedPreference("ai_model_id", "deepseek-chat")

    var systemPrompt by CachedPreference("ai_system_prompt", DEFAULT_SYSTEM_PROMPT)

    var permissionMode by CachedPreference("ai_permission_mode", PermissionMode.ASK.name)

    private var storedDisabledTools by CachedPreference("ai_disabled_tools", "[]")

    /** Tool names the user switched off; every other registered tool is offered to the model. */
    val disabledTools: Set<String>
        get() = decodeToolNames(storedDisabledTools)

    fun isToolEnabled(name: String): Boolean = name !in disabledTools

    fun setToolEnabled(name: String, enabled: Boolean) {
        val current = disabledTools
        storedDisabledTools = encodeToolNames(if (enabled) current - name else current + name)
    }

    /** The credential for [providerId]; each provider keeps its own. */
    fun apiKey(providerId: String): String = Preference.getString(apiKeyPreference(providerId), "")

    fun setApiKey(providerId: String, key: String) {
        Preference.setString(apiKeyPreference(providerId), key.trim())
    }

    fun currentPermissionMode(): PermissionMode =
        PermissionMode.entries.firstOrNull { it.name == permissionMode } ?: PermissionMode.ASK

    private fun apiKeyPreference(providerId: String) = "ai_api_key_" + providerId.replace(NON_ID_CHARS, "_")

    private val NON_ID_CHARS = Regex("[^A-Za-z0-9]")

    private fun decodeToolNames(raw: String): Set<String> =
        runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())
            .toSet()

    private fun encodeToolNames(names: Set<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), names.toList())

    const val DEFAULT_SYSTEM_PROMPT: String =
        "You are Xed AI, a coding assistant embedded in the Xed Editor Android app. " +
            "Be concise and precise. When you propose code changes, name the exact file paths " +
            "and keep edits minimal and focused."
}

enum class PermissionMode {
    ASK,
    ACCEPT_EDITS,
    PLAN,
    YOLO,
}
