package com.rk.ai.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.settings.CachedPreference
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Notes the agent keeps between chats.
 *
 * Stored as a JSON array in the app's preferences, which keeps it observable for the AI settings
 * page and lets it be injected into the system prompt at the start of every run.
 */
object AiMemory {
    private var stored by CachedPreference(KEY, "[]")

    /** The remembered notes, in the order they were saved. Reading this is observable. */
    val entries: List<String>
        get() = decode(stored)

    fun add(content: String) {
        val entry = content.trim()
        if (entry.isEmpty()) return
        stored = encode(entries + entry)
    }

    /**
     * Removes the entry at a 1-based [index] - the same numbering the model is shown - and reports
     * whether anything was removed.
     */
    fun removeAt(index: Int): Boolean {
        val current = entries
        if (index !in 1..current.size) return false
        stored = encode(current.toMutableList().also { it.removeAt(index - 1) })
        return true
    }

    /**
     * Replaces the entry at a 1-based [index], and reports whether anything changed.
     */
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

    /** The block appended to the system prompt, or null when nothing has been remembered yet. */
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

/**
 * A per-chat scratch pad the agent can use to park information outside the conversation.
 *
 * Deliberately not persisted: it belongs to the open chat tab and the controller clears it when that
 * tab is closed, which is what makes it safe to treat as private working space.
 */
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
