@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.transformers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import com.rk.ai.providers.Model
import com.rk.ai.models.UIMessage
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.models.Assistant
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

class TransformerContext(
    val context: Context,
    val model: Model,
    val assistant: Assistant,
    val settings: Settings,
    val conversationModeInjectionIds: Set<Uuid> = emptySet(),
    val conversationLorebookIds: Set<Uuid> = emptySet(),
    val processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
)

interface MessageTransformer {
    /**
     * 消息转换器，用于对消息进行转换
     *
     * 对于输入消息，消息会转换被提供给API模块
     *
     * 对于输出消息，会对消息输出chunk进行转换
     */
    suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages
    }
}

interface InputMessageTransformer : MessageTransformer

interface OutputMessageTransformer : MessageTransformer {
    /**
     * 一个视觉的转换，例如转换think tag为reasoning parts
     * 但是不实际转换消息，因为流式输出需要处理消息delta chunk
     * 不能还没结束生成就transform，因此提供一个visualTransform
     */
    suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages
    }

    /**
     * 消息生成完成后调用
     */
    suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages
    }
}

suspend fun List<UIMessage>.transforms(
    transformers: List<MessageTransformer>,
    context: Context,
    model: Model,
    assistant: Assistant,
    settings: Settings,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
): List<UIMessage> {
    val ctx = TransformerContext(
        context = context,
        model = model,
        assistant = assistant,
        settings = settings,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
        processingStatus = processingStatus
    )
    return transformers.fold(this) { acc, transformer ->
        transformer.transform(ctx, acc)
    }
}

suspend fun List<UIMessage>.visualTransforms(
    transformers: List<MessageTransformer>,
    context: Context,
    model: Model,
    assistant: Assistant,
    settings: Settings,
): List<UIMessage> {
    val ctx = TransformerContext(context, model, assistant, settings)
    return transformers.fold(this) { acc, transformer ->
        if (transformer is OutputMessageTransformer) {
            transformer.visualTransform(ctx, acc)
        } else {
            acc
        }
    }
}

suspend fun List<UIMessage>.onGenerationFinish(
    transformers: List<MessageTransformer>,
    context: Context,
    model: Model,
    assistant: Assistant,
    settings: Settings,
): List<UIMessage> {
    val ctx = TransformerContext(context, model, assistant, settings)
    return transformers.fold(this) { acc, transformer ->
        if (transformer is OutputMessageTransformer) {
            transformer.onGenerationFinish(ctx, acc)
        } else {
            acc
        }
    }
}
