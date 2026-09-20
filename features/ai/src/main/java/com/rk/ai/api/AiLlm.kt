package com.rk.ai.api

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.streaming.StreamFrame
import com.rk.ai.provider.AiProviderRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Who wrote one [AiMessage]. */
enum class AiMessageRole {
    System,
    User,
    Assistant,
}

/** One message of a conversation passed to [AiExtensions.chat]. */
data class AiMessage(val role: AiMessageRole, val text: String) {
    companion object {
        fun system(text: String): AiMessage = AiMessage(AiMessageRole.System, text)

        fun user(text: String): AiMessage = AiMessage(AiMessageRole.User, text)

        fun assistant(text: String): AiMessage = AiMessage(AiMessageRole.Assistant, text)
    }
}

/** Forwards the extension-facing calls to the active provider. */
internal object AiLlm {
    suspend fun complete(prompt: String, systemPrompt: String?, modelId: String?): String {
        requireConfigured()
        val conversation = conversation(listOf(AiMessage.user(prompt)), systemPrompt)
        return run(conversation, modelId)
    }

    fun completeStream(prompt: String, systemPrompt: String?, modelId: String?): Flow<String> =
        stream(conversation(listOf(AiMessage.user(prompt)), systemPrompt), modelId)

    suspend fun chat(messages: List<AiMessage>, modelId: String?): String {
        requireConfigured()
        return run(conversation(messages), modelId)
    }

    fun chatStream(messages: List<AiMessage>, modelId: String?): Flow<String> =
        stream(conversation(messages), modelId)

    private suspend fun run(conversation: Prompt, modelId: String?): String {
        val model = AiProviderRuntime.model(modelId)
        return AiProviderRuntime.executor(modelId).execute(conversation, model).textContent()
    }

    private fun stream(conversation: Prompt, modelId: String?): Flow<String> =
        flow {
            requireConfigured()
            val model = AiProviderRuntime.model(modelId)
            AiProviderRuntime.executor(modelId).executeStreaming(conversation, model).collect { frame ->
                if (frame is StreamFrame.TextDelta) emit(frame.text)
            }
        }

    private fun conversation(
        messages: List<AiMessage>,
        systemPrompt: String? = null,
    ): Prompt =
        prompt("xed-ai-extension") {
            systemPrompt?.takeIf { it.isNotBlank() }?.let { system(it) }
            messages.forEach { message ->
                when (message.role) {
                    AiMessageRole.System -> system(message.text)
                    AiMessageRole.User -> user(message.text)
                    AiMessageRole.Assistant -> assistant(message.text)
                }
            }
        }

    private fun requireConfigured() {
        check(AiProviderRuntime.hasApiKey()) {
            "No API key configured for ${AiProviderRuntime.activeProvider().displayName}. Add one in AI settings."
        }
    }
}
