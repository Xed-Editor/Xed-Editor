@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.providers.providers.openai

import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.Flow
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.models.MessageChunk
import com.rk.ai.models.UIMessage

interface OpenAIImpl {
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>
}
