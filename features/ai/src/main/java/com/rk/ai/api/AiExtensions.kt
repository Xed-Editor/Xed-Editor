package com.rk.ai.api

import com.rk.ai.provider.AiModel
import com.rk.ai.provider.AiModelRegistry
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderRegistry
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolRegistry
import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.StateFlow

/** The single entry point extensions use to extend Xed AI. */
object AiExtensions {
    @Volatile private var builtinsInstalled = false

    fun installBuiltins() {
        if (builtinsInstalled) return
        synchronized(this) {
            if (builtinsInstalled) return
            builtinsInstalled = true
            AiToolRegistry.installBuiltins()
            AiProviderRegistry.installBuiltins()
        }
    }

    val tools: StateFlow<List<AiTool>>
        get() = AiToolRegistry.tools

    val providers: StateFlow<List<AiProvider>>
        get() = AiProviderRegistry.providers

    val models: StateFlow<List<AiModel>>
        get() = AiModelRegistry.models

    /** Adds (or replaces) a tool the model can call. */
    @XedExtensionPoint fun registerTool(tool: AiTool) = AiToolRegistry.registerTool(tool)

    @XedExtensionPoint fun registerTools(vararg tools: AiTool) = AiToolRegistry.registerTools(*tools)

    @XedExtensionPoint fun unregisterTool(tool: AiTool) = AiToolRegistry.unregisterTool(tool)

    @XedExtensionPoint fun unregisterTool(name: String) = AiToolRegistry.unregisterTool(name)

    /** Adds (or replaces) a chat backend. */
    @XedExtensionPoint fun registerProvider(provider: AiProvider) = AiProviderRegistry.registerProvider(provider)

    @XedExtensionPoint fun registerProviders(vararg providers: AiProvider) =
        AiProviderRegistry.registerProviders(*providers)

    @XedExtensionPoint fun unregisterProvider(provider: AiProvider) = AiProviderRegistry.unregisterProvider(provider)

    @XedExtensionPoint fun unregisterProvider(id: String) = AiProviderRegistry.unregisterProvider(id)

    @XedExtensionPoint fun registerModel(model: AiModel) = AiModelRegistry.registerModel(model)

    @XedExtensionPoint fun registerModels(vararg models: AiModel) = AiModelRegistry.registerModels(*models)

    @XedExtensionPoint fun unregisterModel(model: AiModel) = AiModelRegistry.unregisterModel(model)
}
