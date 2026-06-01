package com.rk.ai.agent.transformers

import com.rk.ai.models.UIMessage
import com.rk.ai.agent.files.FilesManager

object Base64ImageToLocalFileTransformer : OutputMessageTransformer {
    var filesManager: FilesManager? = null

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val fm = filesManager ?: return messages
        return messages.map { message ->
            fm.convertBase64ImagePartToLocalFile(message)
        }
    }
}