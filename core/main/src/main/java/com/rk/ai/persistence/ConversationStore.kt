package com.rk.ai.persistence

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.ai.ChatMessage
import com.rk.ai.ConversationState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredConversation(
    val id: String,
    val name: String,
    val messages: List<ConversationMessageDto>,
    val systemPrompt: String,
    val agentName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tokenCount: Int = 0,
)

data class ConversationMessageDto(
    val role: String,
    val content: String,
    val timestamp: Long,
)

object ConversationStore {
    private const val CONVERSATIONS_FILE = "conversations.json"
    private const val MAX_CONVERSATIONS = 50
    private const val MAX_MESSAGES_PER_CONVERSATION = 200

    private val gson = Gson()
    private var conversations: MutableList<StoredConversation> = mutableListOf()
    private var isLoaded = false
    private var contextRef: Context? = null

    fun initialize(context: Context) {
        contextRef = context.applicationContext
    }

    suspend fun loadAll(): List<StoredConversation> = withContext(Dispatchers.IO) {
        if (!isLoaded) {
            val file = getConversationsFile()
            if (file.exists()) {
                try {
                    val json = file.readText()
                    val type = object : TypeToken<List<StoredConversation>>() {}.type
                    conversations = gson.fromJson(json, type) ?: mutableListOf()
                } catch (e: Exception) {
                    conversations = mutableListOf()
                }
            }
            isLoaded = true
        }
        conversations.toList()
    }

    suspend fun save(conversation: StoredConversation): StoredConversation = withContext(Dispatchers.IO) {
        ensureLoaded()
        val existing = conversations.indexOfFirst { it.id == conversation.id }
        val updated = conversation.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conversation.messages.takeLast(MAX_MESSAGES_PER_CONVERSATION),
        )
        if (existing >= 0) {
            conversations[existing] = updated
        } else {
            conversations.add(updated)
            if (conversations.size > MAX_CONVERSATIONS) {
                conversations.sortByDescending { it.updatedAt }
                conversations = conversations.take(MAX_CONVERSATIONS).toMutableList()
            }
        }
        persistToDisk()
        updated
    }

    suspend fun delete(conversationId: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        conversations.removeAll { it.id == conversationId }
        persistToDisk()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        conversations.clear()
        persistToDisk()
    }

    suspend fun get(conversationId: String): StoredConversation? = withContext(Dispatchers.IO) {
        ensureLoaded()
        conversations.find { it.id == conversationId }
    }

    fun toConversationState(stored: StoredConversation): ConversationState {
        val messages = stored.messages.map { dto ->
            when (dto.role) {
                "user" -> ChatMessage.User(dto.content, dto.timestamp)
                "system" -> ChatMessage.System(dto.content, dto.timestamp)
                "assistant" -> ChatMessage.Assistant(dto.content, dto.timestamp)
                else -> ChatMessage.User(dto.content, dto.timestamp)
            }
        }
        return ConversationState(
            messages = messages,
            systemPrompt = stored.systemPrompt,
        )
    }

    fun toStoredConversation(
        id: String,
        name: String,
        state: ConversationState,
        agentName: String,
    ): StoredConversation {
        val messages = state.messages.map { msg ->
            ConversationMessageDto(
                role = msg.role,
                content = msg.content,
                timestamp = msg.timestamp,
            )
        }
        return StoredConversation(
            id = id,
            name = name,
            messages = messages,
            systemPrompt = state.systemPrompt,
            agentName = agentName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun searchConversations(query: String): List<StoredConversation> = withContext(Dispatchers.IO) {
        ensureLoaded()
        val q = query.lowercase()
        conversations.filter { conv ->
            conv.name.lowercase().contains(q) ||
            conv.messages.any { it.content.lowercase().contains(q) }
        }.sortedByDescending { it.updatedAt }
    }

    fun conversationCount(): Int = synchronized(this) { conversations.size }

    private suspend fun ensureLoaded() {
        if (!isLoaded) loadAll()
    }

    private fun persistToDisk() {
        try {
            val file = getConversationsFile()
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(conversations))
        } catch (e: Exception) {
            com.rk.core.diagnostics.DebugConsole.e("ConversationStore", "Failed to persist: ${e.message}")
        }
    }

    private fun getConversationsFile(): File {
        val ctx = contextRef ?: throw IllegalStateException("ConversationStore not initialized")
        return File(ctx.filesDir, CONVERSATIONS_FILE)
    }
}
