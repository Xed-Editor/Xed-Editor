package com.rk.ai.agent.context

data class FileInfo(
    val path: String,
    val summary: String = "",
    val symbols: List<String> = emptyList(),
    val lineCount: Int = 0,
    val lastModified: Long = 0,
)

class ProjectMemory {
    private var projectSummary: String = ""
    private var structureCache: String = ""
    private val fileIndex = mutableMapOf<String, FileInfo>()
    private val symbolIndex = mutableMapOf<String, MutableList<String>>()
    private val storage = mutableMapOf<String, String>()

    fun getCachedSummary(): String = projectSummary
    fun setSummary(summary: String) { projectSummary = summary }

    fun getCachedStructure(): String = structureCache
    fun setStructure(structure: String) { structureCache = structure }

    fun indexFile(path: String, symbols: List<String>, lineCount: Int) {
        fileIndex[path] = FileInfo(path = path, symbols = symbols, lineCount = lineCount, lastModified = System.currentTimeMillis())
    }

    fun getFileInfo(path: String): FileInfo? = fileIndex[path]

    fun hasFile(path: String): Boolean = fileIndex.containsKey(path)

    fun findFiles(query: String): List<String> {
        val lower = query.lowercase()
        return fileIndex.keys.filter { it.lowercase().contains(lower) }
    }

    fun indexSymbol(name: String, filePath: String) {
        symbolIndex.getOrPut(name.lowercase()) { mutableListOf() }.add(filePath)
    }

    fun findSymbol(name: String): List<String> = symbolIndex[name.lowercase()] ?: emptyList()

    fun storeRaw(key: String, value: String) { storage[key] = value }
    fun getRaw(key: String): String? = storage[key]

    fun hasProjectInfo(): Boolean = projectSummary.isNotBlank() || fileIndex.isNotEmpty()

    fun clear() {
        projectSummary = ""
        structureCache = ""
        fileIndex.clear()
        symbolIndex.clear()
        storage.clear()
    }
}
