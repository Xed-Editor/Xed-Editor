package com.rk.ai.provider

import com.rk.ai.icons.Robot
import com.rk.settings.Preference
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A provider the user configured themselves. [id] is generated once and is the key that keeps this provider's API key
 * and model selection apart from every other provider's.
 */
@Serializable
data class CustomProviderSpec(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val requestPath: String = DEFAULT_CHAT_PATH,
    val requiresApiKey: Boolean = true,
    val models: List<String> = emptyList(),
) {
    /** Everything that keeps the provider from being registered. */
    val problem: CustomProviderProblem?
        get() =
            when {
                name.isBlank() -> CustomProviderProblem.MISSING_NAME
                !isHttpUrl(baseUrl) -> CustomProviderProblem.INVALID_BASE_URL
                else -> null
            }

    val isUsable: Boolean
        get() = problem == null

    /** [models] as stored, trimmed and without blanks or duplicates. */
    fun modelIds(): List<String> = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Accepts the ids as typed: one per line, or comma separated. */
    fun modelIdsText(): String = modelIds().joinToString(", ")

    companion object {
        const val DEFAULT_CHAT_PATH = "v1/chat/completions"

        /** True for an `http://` or `https://` address with a host. */
        fun isHttpUrl(value: String): Boolean {
            val trimmed = value.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
            val host = trimmed.substringAfter("://").substringBefore('/').substringBefore('?')
            return host.isNotBlank() && host.none { it.isWhitespace() }
        }

        /** Parses the free-text model field of the editor. */
        fun parseModelIds(text: String): List<String> =
            text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}

enum class CustomProviderProblem {
    MISSING_NAME,
    INVALID_BASE_URL,
}

/**
 * Persists the providers the user added in AI settings and keeps them registered.
 *
 * API keys are not part of the record: they live under the provider id in [com.rk.ai.settings.AiSettings], so the
 * stored provider list never carries a secret.
 */
object CustomProviders {
    private const val PREFERENCE_KEY = "ai_custom_providers"
    private const val ID_PREFIX = "custom-"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(CustomProviderSpec.serializer())

    /**
     * Every stored record, including one that is not usable yet: settings lists it so it can be fixed or removed, while
     * [refresh] registers only the usable ones.
     */
    fun all(): List<CustomProviderSpec> = decode().filter { it.id.startsWith(ID_PREFIX) }.distinctBy { it.id }

    fun find(id: String): CustomProviderSpec? = all().firstOrNull { it.id == id }

    fun isCustom(providerId: String): Boolean = providerId.startsWith(ID_PREFIX)

    /** A record for the "add provider" dialog: [id] is already final. */
    fun newSpec(): CustomProviderSpec = CustomProviderSpec(id = newId())

    /** Adds [spec], or replaces the record with the same [CustomProviderSpec.id]. */
    fun save(spec: CustomProviderSpec) {
        val record = spec.copy(name = spec.name.trim(), baseUrl = spec.baseUrl.trim())
        val next = decode().filter { it.id != record.id } + record
        encode(next)
        refresh()
    }

    fun delete(id: String) {
        encode(decode().filter { it.id != id })
        refresh()
    }

    /** The [AiProvider] that talks to [spec]'s endpoint. */
    fun providerFor(spec: CustomProviderSpec): AiProvider =
        OpenAiCompatibleProvider(
            id = spec.id,
            displayName = spec.name.trim().ifBlank { "Custom provider" },
            icon = Robot,
            baseUrl = spec.baseUrl.trim(),
            requestPath = spec.requestPath.trim().ifBlank { CustomProviderSpec.DEFAULT_CHAT_PATH },
            models = spec.modelIds().map { AiModel(id = it, providerId = spec.id) },
            requiresApiKey = spec.requiresApiKey,
        )

    /** Re-registers every stored provider; call after any change and once at startup. */
    fun refresh() {
        AiProviderRegistry.installCustom(all().filter { it.isUsable }.map(::providerFor))
        AiProviderRuntime.invalidate()
    }

    private fun decode(): List<CustomProviderSpec> = runCatching {
        json.decodeFromString(listSerializer, Preference.getString(PREFERENCE_KEY, "[]"))
    }
        .getOrDefault(emptyList())

    private fun encode(specs: List<CustomProviderSpec>) {
        Preference.setString(PREFERENCE_KEY, json.encodeToString(listSerializer, specs))
    }

    private fun newId(): String = ID_PREFIX + UUID.randomUUID().toString().replace("-", "").take(8)
}
