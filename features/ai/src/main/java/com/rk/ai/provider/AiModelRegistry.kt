package com.rk.ai.provider

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** In registration order. */
    fun forProvider(providerId: String): List<AiModel> = _models.value.filter { it.providerId == providerId }

    internal fun resetForTests() {
        synchronized(lock) {
            registered.clear()
            _models.value = emptyList()
        }
    }
}

object AiModelCatalog {
    fun modelsFor(providerId: String): List<AiModel> {
        val providerModels = AiProviderRegistry.find(providerId)?.models.orEmpty()
        return (providerModels + AiModelRegistry.forProvider(providerId)).distinctBy { it.id }
    }

    fun find(providerId: String, modelId: String): AiModel? = modelsFor(providerId).firstOrNull { it.id == modelId }

    fun defaultFor(provider: AiProvider): AiModel? = modelsFor(provider.id).firstOrNull()
}
