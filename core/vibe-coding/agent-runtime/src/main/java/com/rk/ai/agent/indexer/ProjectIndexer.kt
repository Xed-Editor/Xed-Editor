package com.rk.ai.agent.indexer

import com.rk.ai.service.IdeService
import com.google.gson.JsonElement
import java.io.File

data class ScannedFile(
    val path: String,
    val lastModified: Long,
    val size: Long,
)

data class ModuleInfo(
    val name: String,
    val path: String,
)

data class SymbolInfo(
    val name: String,
    val file: String,
    val line: Int,
    val kind: String,
)

data class DependencyInfo(
    val name: String,
    val version: String?,
    val group: String?,
)

data class IndexResult(
    val files: List<ScannedFile>,
    val modules: List<ModuleInfo>,
    val symbols: List<SymbolInfo>,
    val dependencies: List<DependencyInfo>,
    val packageStructure: Map<String, List<String>>,
)

class ProjectIndexer(private val ideService: IdeService) {

    suspend fun index(workspacePath: String): IndexResult {
        return IndexResult(
            files = scanFiles(workspacePath),
            modules = scanModules(workspacePath),
            symbols = scanSymbols(workspacePath),
            dependencies = scanDependencies(workspacePath),
            packageStructure = scanPackageStructure(workspacePath),
        )
    }

    private suspend fun scanFiles(path: String): List<ScannedFile> {
        val extensions = listOf("kt", "java", "kts", "xml", "gradle", "properties", "json", "yml", "yaml", "toml", "cfg", "md")
        val allFiles = mutableListOf<ScannedFile>()
        for (ext in extensions) {
            try {
                val results = ideService.findFiles("**/*.$ext", 2000, path)
                allFiles.addAll(results.mapNotNull { element ->
                    try {
                        val pathStr = element.asString
                        val file = File(pathStr)
                        if (file.exists()) ScannedFile(pathStr, file.lastModified(), file.length())
                        else null
                    } catch (_: Exception) { null }
                })
            } catch (_: Exception) { }
        }
        return allFiles.distinctBy { it.path }
    }

    private suspend fun scanModules(path: String): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        val settingsContent = try {
            ideService.getFileContent("$path/settings.gradle.kts")
        } catch (_: Exception) { null }
        if (settingsContent != null) {
            val modulePattern = Regex("""include\(":([^"]+)"\)""")
            for (match in modulePattern.findAll(settingsContent)) {
                val moduleName = match.groupValues[1]
                val modulePath = "$path/${moduleName.replace(":", "/")}"
                modules.add(ModuleInfo(moduleName, modulePath))
            }
        }
        return modules
    }

    private suspend fun scanSymbols(path: String): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()
        val patterns = listOf(
            Regex("""^(class|object|interface|data class|sealed class|enum class|abstract class)\s+(\w+)""", RegexOption.MULTILINE),
            Regex("""^fun\s+(\w+)""", RegexOption.MULTILINE),
            Regex("""^val\s+(\w+)\s""", RegexOption.MULTILINE),
            Regex("""^var\s+(\w+)\s""", RegexOption.MULTILINE),
        )

        val files = scanFiles(path).take(200)
        for (file in files) {
            if (file.size > 500_000) continue
            val content = try { File(file.path).readText() } catch (_: Exception) { continue }
            for (pattern in patterns) {
                for (match in pattern.findAll(content)) {
                    val kind = match.groupValues[1].let { raw ->
                        when {
                            raw.startsWith("class") || raw.startsWith("data class") || raw.startsWith("sealed class") || raw.startsWith("abstract class") || raw.startsWith("enum class") -> "class"
                            raw == "object" || raw == "interface" -> raw
                            raw == "fun" -> "fun"
                            raw == "val" || raw == "var" -> "property"
                            else -> raw
                        }
                    }
                    val name = match.groupValues[2]
                    val line = content.substring(0, match.range.first).count { it == '\n' } + 1
                    symbols.add(SymbolInfo(name, file.path, line, kind))
                }
            }
        }
        return symbols
    }

    private suspend fun scanDependencies(path: String): List<DependencyInfo> {
        val deps = mutableListOf<DependencyInfo>()
        val libsContent = try {
            ideService.getFileContent("$path/gradle/libs.versions.toml")
        } catch (_: Exception) { null }
        if (libsContent != null) {
            val libPattern = Regex("""(\S+)\s*=\s*"([^"]+)"(?::"([^"]+)")?""")
            for (match in libPattern.findAll(libsContent)) {
                deps.add(DependencyInfo(match.groupValues[1], match.groupValues[2], match.groupValues[3]))
            }
        }
        return deps
    }

    private suspend fun scanPackageStructure(path: String): Map<String, List<String>> {
        val structure = mutableMapOf<String, MutableList<String>>()
        val files = scanFiles(path).filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }
        for (file in files) {
            val content = try { File(file.path).readLines().firstOrNull() ?: "" } catch (_: Exception) { continue }
            val pkgMatch = Regex("""^package\s+([\w.]+)""").find(content)
            if (pkgMatch != null) {
                val pkg = pkgMatch.groupValues[1]
                structure.getOrPut(pkg) { mutableListOf() }.add(file.path)
            }
        }
        return structure
    }
}
