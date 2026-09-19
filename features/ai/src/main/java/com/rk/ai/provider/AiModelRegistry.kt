package com.rk.ai.provider

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Extra models that are not shipped on their [AiProvider].
 *
 * An extension uses this to add a model to an existing provider - for example a newer DeepSeek
 * release - without reimplementing the provider. Built-in providers declare their own models; both
 * sets are merged by [AiModelCatalog].
 */
object AiModelRegistry {
    private val lock = Any()
    private val registered = LinkedHashMap<String, AiModel>()
    private val _models = MutableStateFlow<List<AiModel>>(emptyList())

    val models: StateFlow<List<AiModel>> = _models.asStateFlow()

    @XedExtensionPoint
    fun registerModel(model: AiModel) {
        synchronized(lock) {
            registered["${model.providerId}/${model.id}"] = model
            _models.value = registered.values.toList()
        }
    }

    @XedExtensionPoint
    fun registerModels(vararg models: AiModel) = models.forEach(::registerModel)

    @XedExtensionPoint
    fun unregisterModel(model: AiModel) {
        synchronized(lock) {
            if (registered.remove("${model.providerId}/${model.id}") != null) {
                _models.value = registered.values.toList()
            }
        }
    }

    /** Models registered for [providerId], in registration order. */
    fun forProvider(providerId: String): List<AiModel> = _models.value.filter { it.providerId == providerId }

    internal fun resetForTests() {
        synchronized(lock) {
            registered.clear()
            _models.value = emptyList()
        }
    }
}

/** Merges a provider's shipped models with the ones extensions added for it. */
object AiModelCatalog {
    fun modelsFor(providerId: String): List<AiModel> {
        val providerModels = AiProviderRegistry.find(providerId)?.models.orEmpty()
        return (providerModels + AiModelRegistry.forProvider(providerId)).distinctBy { it.id }
    }

    fun find(providerId: String, modelId: String): AiModel? = modelsFor(providerId).firstOrNull { it.id == modelId }

    /** The model to select when a provider is chosen and the current id does not belong to it. */
    fun defaultFor(provider: AiProvider): AiModel? = modelsFor(provider.id).firstOrNull()
}
