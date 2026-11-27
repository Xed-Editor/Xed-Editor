package com.rk.ai.settings

import androidx.compose.runtime.mutableStateOf
import com.rk.ai.provider.AiProviderRegistry
import com.rk.settings.CachedPreference
import com.rk.settings.Preference
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object AiSettings {
    /** Must match the DeepSeek built-in provider id. */
    const val DEFAULT_PROVIDER_ID = "deepseek"

    var providerId by CachedPreference("ai_provider_id", DEFAULT_PROVIDER_ID)

    var systemPrompt by CachedPreference("ai_system_prompt", DEFAULT_SYSTEM_PROMPT)

    var permissionMode by CachedPreference("ai_permission_mode", PermissionMode.ASK.name)

    /**
     * Bumped whenever any provider's model selection changes, so composables that read a selection through [modelId]
     * recompose even though the value itself comes straight from preferences.
     */
    private val selectionRevision = mutableStateOf(0)

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

    fun clearApiKey(providerId: String) {
        Preference.setString(apiKeyPreference(providerId), "")
    }

    /** The model chosen for [providerId], or blank when the provider's first model should be used. */
    fun modelId(providerId: String): String {
        // Composition reads this to stay live; the value itself is not cached here.
        selectionRevision.value
        return Preference.getString(modelPreference(providerId), "")
    }

    fun setModelId(providerId: String, modelId: String) {
        Preference.setString(modelPreference(providerId), modelId.trim())
        selectionRevision.value++
    }

    /**
     * Moves the single model selection older versions stored into the per-provider key, then drops it so it cannot be
     * inherited by a provider the user switches to later.
     */
    fun migrateLegacyModelSelection() {
        val legacy = Preference.getString(LEGACY_MODEL_ID_KEY, "").trim()
        if (legacy.isEmpty()) return
        Preference.setString(LEGACY_MODEL_ID_KEY, "")
        // The old shipped default is retired, so leave it to the provider's own first model instead.
        if (legacy == LEGACY_DEFAULT_MODEL || modelId(providerId).isNotBlank()) return
        setModelId(providerId, legacy)
    }

    /** Falls back to the first available provider when the selected one is no longer registered. */
    fun resetUnavailableProvider() {
        if (providerId.isEmpty()) return
        val available = AiProviderRegistry.providers.value
        if (available.isEmpty() || available.any { it.id == providerId }) return
        providerId = available.first().id
    }

    fun currentPermissionMode(): PermissionMode =
        PermissionMode.entries.firstOrNull { it.name == permissionMode } ?: PermissionMode.ASK

    private fun apiKeyPreference(providerId: String) = "ai_api_key_" + providerId.replace(NON_ID_CHARS, "_")

    private fun modelPreference(providerId: String) = "ai_model_id_" + providerId.replace(NON_ID_CHARS, "_")

    private val NON_ID_CHARS = Regex("[^A-Za-z0-9]")

    private fun decodeToolNames(raw: String): Set<String> = runCatching {
        Json.decodeFromString(ListSerializer(String.serializer()), raw)
    }
        .getOrDefault(emptyList())
        .toSet()

    private fun encodeToolNames(names: Set<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), names.toList())

    /** Where versions before per-provider models kept the one selected model. */
    private const val LEGACY_MODEL_ID_KEY = "ai_model_id"

    /** The model that shipped as the default before per-provider selections existed. */
    private const val LEGACY_DEFAULT_MODEL = "deepseek-chat"

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
