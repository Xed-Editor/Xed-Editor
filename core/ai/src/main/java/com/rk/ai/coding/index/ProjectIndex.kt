package com.rk.ai.coding.index

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.ai.service.LspOps
import com.rk.ai.service.ProjectOps

data class IndexedSymbol(
    val name: String,
    val path: String,
    val line: Int,
    val column: Int,
    val snippet: String,
    val kind: SymbolKind = SymbolKind.Unknown,
)

data class CodeReference(
    val uri: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)

enum class SymbolKind {
    Class,
    Function,
    Symbol,
    Unknown,
}

class ProjectIndex(
    private val symbolIndex: SymbolIndex,
    private val referenceIndex: ReferenceIndex,
) {
    constructor(projectOps: ProjectOps, lspOps: LspOps) : this(
        symbolIndex = SymbolIndex(projectOps),
        referenceIndex = ReferenceIndex(lspOps),
    )

    suspend fun findClass(name: String, limit: Int = 20, path: String? = null): List<IndexedSymbol> =
        symbolIndex.findClass(name, limit, path)

    suspend fun findFunction(name: String, limit: Int = 20, path: String? = null): List<IndexedSymbol> =
        symbolIndex.findFunction(name, limit, path)

    suspend fun findSymbol(name: String, limit: Int = 20, path: String? = null): List<IndexedSymbol> =
        symbolIndex.findSymbol(name, limit, path)

    suspend fun findReferences(filePath: String, line: Int, column: Int): List<CodeReference> =
        referenceIndex.findReferences(filePath, line, column)
}

class SymbolIndex(
    private val projectOps: ProjectOps,
) {
    suspend fun findClass(name: String, limit: Int = 20, path: String? = null): List<IndexedSymbol> =
        findSymbol(name, limit * 2, path)
            .filter { it.kind == SymbolKind.Class || classPattern(name).containsMatchIn(it.snippet) }
            .take(limit)

    suspend fun findFunction(name: String, limit: Int = 20, path: String? = null): List<IndexedSymbol> =
        findSymbol(name, limit * 2, path)
            .filter { it.kind == SymbolKind.Function || functionPattern(name).containsMatchIn(it.snippet) }
            .take(limit)

    suspend fun findSymbol(name: String, limit: Int = 20, path: String? = null): List<IndexedSymbol> =
        projectOps.searchSymbols(name, limit, path)
            .mapNotNull { it.toIndexedSymbol(name) }

    private fun JsonElement.toIndexedSymbol(query: String): IndexedSymbol? {
        val obj = runCatching { asJsonObject }.getOrNull() ?: return null
        val snippet = obj.string("snippet")
        return IndexedSymbol(
            name = query,
            path = obj.string("path"),
            line = obj.int("line") ?: 1,
            column = obj.int("column") ?: 1,
            snippet = snippet,
            kind = when {
                classPattern(query).containsMatchIn(snippet) -> SymbolKind.Class
                functionPattern(query).containsMatchIn(snippet) -> SymbolKind.Function
                snippet.isNotBlank() -> SymbolKind.Symbol
                else -> SymbolKind.Unknown
            },
        )
    }

    private fun classPattern(name: String): Regex =
        Regex("""\b(class|interface|object|enum|struct)\s+${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE)

    private fun functionPattern(name: String): Regex =
        Regex("""\b(fun|def|function)\s+${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE)
}

class ReferenceIndex(
    private val lspOps: LspOps,
) {
    suspend fun findDefinitions(filePath: String, line: Int, column: Int): List<CodeReference> =
        lspOps.findDefinitions(filePath, line, column).mapNotNull { it.toReference() }

    suspend fun findReferences(filePath: String, line: Int, column: Int): List<CodeReference> =
        lspOps.findReferences(filePath, line, column).mapNotNull { it.toReference() }

    private fun JsonElement.toReference(): CodeReference? {
        val obj = runCatching { asJsonObject }.getOrNull() ?: return null
        val range = obj.get("range")?.takeIf { it.isJsonObject }?.asJsonObject
        val start = range?.get("start")?.takeIf { it.isJsonObject }?.asJsonObject
        val end = range?.get("end")?.takeIf { it.isJsonObject }?.asJsonObject
        return CodeReference(
            uri = obj.string("uri"),
            startLine = start?.int("line") ?: 1,
            startColumn = start?.int("character") ?: 1,
            endLine = end?.int("line") ?: 1,
            endColumn = end?.int("character") ?: 1,
        )
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString.orEmpty()

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt
