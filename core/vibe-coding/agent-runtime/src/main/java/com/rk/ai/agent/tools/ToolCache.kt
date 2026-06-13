package com.rk.ai.agent.tools

import com.rk.ai.models.UIMessagePart

data class CachedToolResult(
    val result: List<UIMessagePart>,
    val timestamp: Long,
    val hitCount: Int = 0,
)

class ToolCache(
    private val maxEntries: Int = 100,
    private val ttlMs: Long = 120_000L,
) {
    private data class Entry(
        val result: List<UIMessagePart>,
        val timestamp: Long,
        var hitCount: Int = 0,
    )

    private val cache = LinkedHashMap<String, Entry>(maxEntries, 0.75f, true)

    private val READ_TOOLS = setOf(
        "getProjectStructure", "getProjectSummary", "getProjectConfig",
        "listFiles", "ls", "getOpenFiles", "searchCode", "grep",
        "searchSymbols", "getProjectInstructions", "searchProjectInstructions",
        "indexCodebase", "semanticSearch", "getGuidelines",
        "getEnvironment", "getIdeInfo",
    )

    fun get(toolName: String, argsHash: String): List<UIMessagePart>? {
        if (!isCacheable(toolName)) return null
        val key = makeKey(toolName, argsHash)
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key)
            return null
        }
        entry.hitCount++
        return entry.result
    }

    fun put(toolName: String, argsHash: String, result: List<UIMessagePart>) {
        if (!isCacheable(toolName)) return
        val key = makeKey(toolName, argsHash)
        if (cache.size >= maxEntries) cache.remove(cache.keys.first())
        cache[key] = Entry(result, System.currentTimeMillis())
    }

    fun invalidate(toolName: String) {
        cache.keys.removeAll { it.startsWith("$toolName:") }
    }

    fun invalidateAll() { cache.clear() }

    fun invalidateProjectCache() {
        for (tool in setOf("getProjectStructure", "getProjectSummary", "getProjectConfig", "listFiles", "ls", "indexCodebase")) {
            invalidate(tool)
        }
    }

    val stats: String get() = buildString {
        appendLine("Tool Cache: ${cache.size}/$maxEntries entries")
        val totalHits = cache.values.sumOf { it.hitCount }
        appendLine("Total hits: $totalHits")
        cache.entries.sortedByDescending { it.value.hitCount }.take(10).forEach { (key, entry) ->
            val age = (System.currentTimeMillis() - entry.timestamp) / 1000
            appendLine("  $key: ${entry.hitCount} hits, ${age}s old")
        }
    }

    private fun makeKey(toolName: String, argsHash: String): String = "$toolName:$argsHash"

    private fun isCacheable(name: String): Boolean = name in READ_TOOLS
}
