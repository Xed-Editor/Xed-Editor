package com.rk.ai.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.settings.CachedPreference
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Stored as a JSON array in the app's preferences. */
object AiMemory {
    private var stored by CachedPreference(KEY, "[]")

    /** In save order; reading this is observable. */
    val entries: List<String>
        get() = decode(stored)

    fun add(content: String) {
        val entry = content.trim()
        if (entry.isEmpty()) return
        stored = encode(entries + entry)
    }

    /** [index] is 1-based, matching the numbering shown to the model. */
    fun removeAt(index: Int): Boolean {
        val current = entries
        if (index !in 1..current.size) return false
        stored = encode(current.toMutableList().also { it.removeAt(index - 1) })
        return true
    }

    fun updateAt(index: Int, content: String): Boolean {
        val entry = content.trim()
        val current = entries
        if (index !in 1..current.size || entry.isEmpty()) return false
        stored = encode(current.toMutableList().also { it[index - 1] = entry })
        return true
    }

    fun clear() {
        stored = "[]"
    }

    /** Appended to the system prompt; null when nothing is remembered. */
    fun promptSection(): String? {
        val current = entries
        if (current.isEmpty()) return null
        return buildString {
                appendLine("Long-term memory carried over from earlier chats. Save new notes with")
                appendLine("`save_memory` and drop stale ones with `forget_memory` by their number.")
                current.forEachIndexed { index, entry -> appendLine("${index + 1}. $entry") }
            }
            .trim()
    }

    private fun decode(raw: String): List<String> =
        runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())

    private fun encode(list: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), list)

    private const val KEY = "ai_memory"
}

/** Deliberately not persisted; cleared when the chat tab closes. */
object AiScratchpad {
    var text by mutableStateOf("")
        private set

    fun write(content: String) {
        text = content
    }

    fun append(content: String) {
        val addition = content.trimEnd()
        text = if (text.isBlank()) addition else text.trimEnd() + "\n" + addition
    }

    fun clear() {
        text = ""
    }
}
