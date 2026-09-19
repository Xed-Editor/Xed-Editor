package com.rk.ai.api

import com.rk.ai.provider.AiModel
import com.rk.ai.provider.AiModelRegistry
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderRegistry
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolRegistry
import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.StateFlow

/**
 * The single entry point extensions use to extend Xed AI.
 *
 * Everything here is safe to call from `ExtensionAPI.onLoad`, and every registration should be undone
 * from `onDispose` with the matching `unregister*` call so an uninstalled extension leaves no
 * capabilities behind. Registries are observable and are read at the start of each agent run, so
 * tools, providers and models added at runtime take effect without a restart.
 *
 * ```kotlin
 * class MyExtension(context: ExtensionContext) : ExtensionAPI(context) {
 *     private val weather = aiTool("weather", "Look up the weather.", AiToolKind.Network) {
 *         stringParam("city", "City to look up")
 *         executes { args -> fetch(args.requireArg("city")) }
 *     }
 *
 *     override fun onLoad() {
 *         AiExtensions.registerTool(weather)
 *     }
 *
 *     override fun onDispose() {
 *         AiExtensions.unregisterTool(weather)
 *     }
 * }
 * ```
 */
object AiExtensions {
    @Volatile private var builtinsInstalled = false

    /**
     * Installs the built-in tools, providers and models. Called during feature start-up and
     * idempotent, so extensions never need to call it.
     */
    fun installBuiltins() {
        if (builtinsInstalled) return
        synchronized(this) {
            if (builtinsInstalled) return
            builtinsInstalled = true
            AiToolRegistry.installBuiltins()
            AiProviderRegistry.installBuiltins()
        }
    }

    /** Every registered tool, including built-ins and other extensions' tools. */
    val tools: StateFlow<List<AiTool>>
        get() = AiToolRegistry.tools

    /** Every registered provider. */
    val providers: StateFlow<List<AiProvider>>
        get() = AiProviderRegistry.providers

    /** Models added by extensions, on top of the ones providers ship with. */
    val models: StateFlow<List<AiModel>>
        get() = AiModelRegistry.models

    // Tools -------------------------------------------------------------------------------------

    /** Adds (or replaces) a tool the model can call. */
    @XedExtensionPoint fun registerTool(tool: AiTool) = AiToolRegistry.registerTool(tool)

    @XedExtensionPoint fun registerTools(vararg tools: AiTool) = AiToolRegistry.registerTools(*tools)

    @XedExtensionPoint fun unregisterTool(tool: AiTool) = AiToolRegistry.unregisterTool(tool)

    @XedExtensionPoint fun unregisterTool(name: String) = AiToolRegistry.unregisterTool(name)

    // Providers ---------------------------------------------------------------------------------

    /** Adds (or replaces) a chat backend. Providers usually declare their own models. */
    @XedExtensionPoint fun registerProvider(provider: AiProvider) = AiProviderRegistry.registerProvider(provider)

    @XedExtensionPoint fun registerProviders(vararg providers: AiProvider) =
        AiProviderRegistry.registerProviders(*providers)

    @XedExtensionPoint fun unregisterProvider(provider: AiProvider) = AiProviderRegistry.unregisterProvider(provider)

    @XedExtensionPoint fun unregisterProvider(id: String) = AiProviderRegistry.unregisterProvider(id)

    // Models ------------------------------------------------------------------------------------

    /** Adds a model to an existing provider, e.g. a newly released model id. */
    @XedExtensionPoint fun registerModel(model: AiModel) = AiModelRegistry.registerModel(model)

    @XedExtensionPoint fun registerModels(vararg models: AiModel) = AiModelRegistry.registerModels(*models)

    @XedExtensionPoint fun unregisterModel(model: AiModel) = AiModelRegistry.unregisterModel(model)
}
