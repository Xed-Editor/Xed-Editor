package com.rk.ai

sealed class ChatMessage {
    abstract val content: String
    abstract val timestamp: Long
    open val role: String get() = when (this) {
        is User -> "user"
        is Assistant -> "assistant"
        is System -> "system"
    }

    data class User(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage()

    data class Assistant(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val isStreaming: Boolean = false,
    ) : ChatMessage()

    data class System(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage()
}

data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val systemPrompt: String = "",
) {
    val isStreaming: Boolean get() = streamingText.isNotBlank()

    fun addUserMessage(text: String): ConversationState = copy(
        messages = messages + ChatMessage.User(text),
        isLoading = true,
        error = null,
    )

    fun appendStreaming(text: String): ConversationState = copy(
        streamingText = streamingText + text,
    )

    fun finishStreaming(): ConversationState = copy(
        messages = if (streamingText.isNotBlank()) {
            messages + ChatMessage.Assistant(streamingText.trim())
        } else messages,
        isLoading = false,
        streamingText = "",
    )

    fun setError(error: String): ConversationState = copy(
        isLoading = false,
        streamingText = "",
        error = error,
    )

    fun clear(): ConversationState = ConversationState()

    fun buildContextPrompt(newPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.appendLine("System: $systemPrompt")
            sb.appendLine()
        }
        messages.takeLast(10).forEach { msg ->
            when (msg) {
                is ChatMessage.User -> sb.appendLine("User: ${msg.content}")
                is ChatMessage.Assistant -> sb.appendLine("Assistant: ${msg.content}")
                is ChatMessage.System -> sb.appendLine("System: ${msg.content}")
            }
            sb.appendLine()
        }
        sb.appendLine("User: $newPrompt")
        return sb.toString()
    }
}
