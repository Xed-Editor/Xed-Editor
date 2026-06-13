package com.rk.ai.agent.indexer

class ProjectKnowledgeBase(private val index: IndexResult) {

    fun findClass(className: String): List<SymbolInfo> =
        index.symbols.filter { it.name == className && (it.kind == "class" || it.kind == "interface" || it.kind == "object") }

    fun findFunction(name: String): List<SymbolInfo> =
        index.symbols.filter { it.name == name && it.kind == "fun" }

    fun findSymbol(name: String): List<SymbolInfo> =
        index.symbols.filter { it.name == name }

    fun searchSymbols(query: String): List<SymbolInfo> {
        val lower = query.lowercase()
        return index.symbols.filter { it.name.lowercase().contains(lower) || it.file.lowercase().contains(lower) }
    }

    fun getFilesInPackage(pkg: String): List<String> =
        index.packageStructure[pkg] ?: emptyList()

    fun getModuleForFile(filePath: String): ModuleInfo? =
        index.modules.find { filePath.startsWith(it.path) }

    fun findDependency(name: String): DependencyInfo? =
        index.dependencies.find { it.name.contains(name, ignoreCase = true) }

    fun findFilesRelevantTo(query: String): List<String> {
        val lower = query.lowercase()
        val scored = index.files.map { file ->
            val score = listOf(
                if (file.path.lowercase().contains(lower)) 10 else 0,
                if (lower.split(" ").any { file.path.lowercase().contains(it) }) 5 else 0,
                if (file.path.endsWith(".kt") || file.path.endsWith(".java")) 2 else 0,
            ).sum()
            file.path to score
        }
        return scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }

    fun getSourceFiles(): List<String> =
        index.files.filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }.map { it.path }

    val summary: String get() = buildString {
        appendLine("Project Knowledge Base")
        appendLine("  Files: ${index.files.size}")
        appendLine("  Modules: ${index.modules.size}")
        appendLine("  Symbols: ${index.symbols.size}")
        appendLine("  Dependencies: ${index.dependencies.size}")
        appendLine("  Packages: ${index.packageStructure.size}")
        val kotlinFiles = index.files.count { it.path.endsWith(".kt") }
        val javaFiles = index.files.count { it.path.endsWith(".java") }
        appendLine("  Kotlin files: $kotlinFiles, Java files: $javaFiles")
    }
}
