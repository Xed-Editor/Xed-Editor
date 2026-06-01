@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.rk.ai.agent.transformers

import com.rk.ai.core.MessageRole
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.AssistantAffectScope
import com.rk.ai.models.replaceRegexes

object RegexOutputTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val assistant = ctx.assistant
        if (assistant.regexes.isEmpty()) return messages
        return messages.map { message ->
            val scope = when (message.role) {
                MessageRole.ASSISTANT -> AssistantAffectScope.ASSISTANT
                else -> return@map message
            }
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            part.copy(text = part.text.replaceRegexes(assistant, scope, visual = false))
                        }
                        is UIMessagePart.Reasoning -> {
                            part.copy(reasoning = part.reasoning.replaceRegexes(assistant, scope, visual = false))
                        }
                        else -> part
                    }
                }
            )
        }
    }
}