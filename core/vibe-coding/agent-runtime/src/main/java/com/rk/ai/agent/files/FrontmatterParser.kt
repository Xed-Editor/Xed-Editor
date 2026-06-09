package com.rk.ai.agent.files

import android.util.Log

sealed class FrontmatterResult<out T> {
    data class Success<T>(val frontmatter: Map<String, String>, val body: String, val parsed: T) : FrontmatterResult<T>()
    data class Partial<T>(val frontmatter: Map<String, String>, val body: String, val parsed: T, val warnings: List<String>) : FrontmatterResult<T>()
    data class Error(val message: String, val filePath: String? = null) : FrontmatterResult<Nothing>()
}

object FrontmatterParser {
    private const val TAG = "FrontmatterParser"
    private val frontmatterEndRegex = Regex("""\r?\n---(?:\r?\n|$)""")

    fun parseRaw(content: String): Pair<Map<String, String>, String> {
        if (!content.startsWith("---")) return emptyMap() to content.trim()

        val endRange = findFrontmatterEndRange(content)
        if (endRange == null) {
            Log.w(TAG, "Frontmatter start marker found but no end marker")
            return emptyMap() to content.trim()
        }

        val yaml = content.substring(3, endRange.first).trim()
        val body = content.substring(endRange.last + 1).trimStart('\r', '\n')

        val fields = mutableMapOf<String, String>()
        yaml.lines().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
                if (key.isNotBlank()) {
                    fields[key] = value
                }
            }
        }
        return fields to body
    }

    fun parse<T>(content: String, filePath: String? = null, builder: (Map<String, String>, String) -> T): FrontmatterResult<T> {
        return try {
            val (frontmatter, body) = parseRaw(content)
            val result = builder(frontmatter, body)
            Success(frontmatter, body, result)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse frontmatter from $filePath", e)
            Error(e.message ?: "Unknown parse error", filePath)
        }
    }

    fun extractBody(content: String): String {
        if (!content.startsWith("---")) return content.trim()
        val endRange = findFrontmatterEndRange(content) ?: return content.trim()
        return content.substring(endRange.last + 1).trimStart('\r', '\n')
    }

    fun extractFrontmatter(content: String): Map<String, String> {
        return parseRaw(content).first
    }

    fun splitNameValue(line: String): Pair<String, String>? {
        val colonIdx = line.indexOf(':')
        if (colonIdx <= 0) return null
        val key = line.substring(0, colonIdx).trim()
        val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
        if (key.isBlank()) return null
        return key to value
    }

    private fun findFrontmatterEndRange(content: String): IntRange? {
        if (!content.startsWith("---")) return null
        return frontmatterEndRegex.find(content, startIndex = 3)?.range
    }
}

data class FrontmatterSpec(
    val name: String? = null,
    val description: String? = null,
    val hidden: Boolean = false,
    val model: String? = null,
)

fun Map<String, String>.getFrontmatterString(key: String): String? = this[key]
fun Map<String, String>.getFrontmatterBoolean(key: String, default: Boolean = false): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: default
fun Map<String, String>.getFrontmatterStringList(key: String): List<String> =
    this[key]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
