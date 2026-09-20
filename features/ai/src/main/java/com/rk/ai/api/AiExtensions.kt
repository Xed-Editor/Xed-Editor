package com.rk.ai.api

import ai.koog.prompt.executor.model.PromptExecutor
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.ai.provider.AiModel
import com.rk.ai.provider.AiModelRegistry
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderConfig
import com.rk.ai.provider.AiProviderRegistry
import com.rk.ai.provider.AiProviderRuntime
import com.rk.ai.provider.OpenAiCompatibleProvider
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolRegistry
import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.Flow
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

    /** True when [ask] and [chat] can run: a provider is active and, when required, has an API key. */
    val isReady: Boolean
        get() = AiProviderRuntime.hasApiKey()

    /** The provider configured in AI settings. */
    val activeProvider: AiProvider
        get() = AiProviderRuntime.activeProvider()

    /** The model [ask] and [chat] use when no `modelId` override is passed. */
    val activeModelId: String
        get() = AiProviderRuntime.model().id

    /**
     * Sends [prompt] to the active model and returns the reply text.
     *
     * @param systemPrompt optional instruction prepended to the conversation.
     * @param modelId model to use instead of the one selected in AI settings.
     * @throws IllegalStateException when [isReady] is false.
     */
    @XedExtensionPoint
    suspend fun ask(prompt: String, systemPrompt: String? = null, modelId: String? = null): String =
        AiLlm.complete(prompt, systemPrompt, modelId)

    /** Like [ask], but emits the reply text as it streams in. */
    @XedExtensionPoint
    fun askStream(prompt: String, systemPrompt: String? = null, modelId: String? = null): Flow<String> =
        AiLlm.completeStream(prompt, systemPrompt, modelId)

    /** Sends a full [messages] conversation and returns the reply text. */
    @XedExtensionPoint
    suspend fun chat(messages: List<AiMessage>, modelId: String? = null): String = AiLlm.chat(messages, modelId)

    /** Like [chat], but emits the reply text as it streams in. */
    @XedExtensionPoint
    fun chatStream(messages: List<AiMessage>, modelId: String? = null): Flow<String> =
        AiLlm.chatStream(messages, modelId)

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

    /** Registers an OpenAI-compatible backend without implementing [AiProvider]. */
    @XedExtensionPoint
    fun registerOpenAiCompatibleProvider(
        id: String,
        displayName: String,
        icon: ImageVector,
        baseUrl: String,
        requestPath: String = "v1/chat/completions",
        models: List<AiModel> = emptyList(),
        requiresApiKey: Boolean = true,
        createExecutor: ((AiProviderConfig) -> PromptExecutor)? = null,
    ): AiProvider {
        val provider =
            OpenAiCompatibleProvider(
                id = id,
                displayName = displayName,
                icon = icon,
                baseUrl = baseUrl,
                requestPath = requestPath,
                models = models,
                requiresApiKey = requiresApiKey,
                executorFactory = createExecutor,
            )
        registerProvider(provider)
        return provider
    }

    @XedExtensionPoint fun registerModel(model: AiModel) = AiModelRegistry.registerModel(model)

    @XedExtensionPoint fun registerModels(vararg models: AiModel) = AiModelRegistry.registerModels(*models)

    @XedExtensionPoint fun unregisterModel(model: AiModel) = AiModelRegistry.unregisterModel(model)
}
