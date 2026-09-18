package com.rk.ai.provider

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.util.Log
import com.rk.ai.settings.AiSettings

object AiProvider {
    private var cachedKey: String? = null
    private var cachedExecutor: PromptExecutor? = null

    fun model(): LLModel =
        LLModel(
            provider = LLMProvider.OpenAI,
            id = AiSettings.modelId,
            capabilities =
                listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.ToolChoice,
                    LLMCapability.Completion,
                    LLMCapability.OpenAIEndpoint.Completions,
                ),
        )

    @Synchronized
    fun executor(): PromptExecutor {
        val key = "${AiSettings.apiKey}|${AiSettings.baseUrl}|${AiSettings.chatCompletionsPath}"
        cachedExecutor?.let { existing ->
            if (cachedKey == key) return existing
        }

        runCatching { cachedExecutor?.close() }

        Log.i(
            TAG,
            "Building Koog executor: baseUrl=${AiSettings.baseUrl} " +
                "path=${AiSettings.chatCompletionsPath} model=${AiSettings.modelId}",
        )

        val executor =
            MultiLLMPromptExecutor(
                OpenAILLMClient(
                    apiKey = AiSettings.apiKey,
                    settings =
                        OpenAIClientSettings(
                            baseUrl = AiSettings.baseUrl,
                            chatCompletionsPath = AiSettings.chatCompletionsPath,
                        ),
                    httpClientFactory = KtorKoogHttpClient.Factory(),
                )
            )

        cachedKey = key
        cachedExecutor = executor
        return executor
    }
}

private const val TAG = "XedAI"
