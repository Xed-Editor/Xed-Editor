package com.rk.ai.agent.transformers

import com.rk.ai.core.MessageRole
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart

object ToolTagSanitizerTransformer : OutputMessageTransformer {

    private val toolTagPattern = Regex(
        """</?tool[_\s]?call[^>]*>|</?invoke[^>]*>|</?tool_calls[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            var changed = false
            val newParts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        val cleaned = part.text.replace(toolTagPattern) { match ->
                            changed = true
                            ""
                        }
                        if (cleaned != part.text) part.copy(text = cleaned.trim()) else part
                    }
                    else -> part
                }
            }
            if (changed) message.copy(parts = newParts) else message
        }
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            var changed = false
            val newParts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        val cleaned = part.text.replace(toolTagPattern) { match ->
                            changed = true
                            ""
                        }
                        if (cleaned != part.text) part.copy(text = cleaned.trim()) else part
                    }
                    else -> part
                }
            }
            if (changed) message.copy(parts = newParts) else message
        }
    }
}
