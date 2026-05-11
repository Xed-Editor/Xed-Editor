package com.rk.ai

import com.rk.file.sandboxHomeDir
import java.io.File
import java.net.URI

private val fallbackWorkspaceRoots = listOf("/home", "/storage/emulated/0")

internal fun geminiIdeWorkspacePath(primary: String): String =
    (listOf(primary) + fallbackWorkspaceRoots)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(File.pathSeparator)

internal fun geminiWorkspaceRoots(workspacePath: String): List<File> =
    geminiIdeWorkspacePath(workspacePath)
        .split(File.pathSeparator)
        .mapNotNull { root -> runCatching { geminiAndroidFileForProotPath(File(root)).canonicalFile }.getOrNull() }
        .distinctBy { it.path }

internal fun geminiDisplayRootFor(workspacePath: String, file: File): File =
    geminiWorkspaceRoots(workspacePath)
        .filter { root -> geminiIsInsideRoot(file, root) && root.path != File.separator }
        .maxByOrNull { it.path.length }
        ?: geminiWorkspaceRoots(workspacePath).firstOrNull()
        ?: file

internal fun geminiResolveWorkspacePath(workspacePath: String, path: String): File? {
    val roots = geminiWorkspaceRoots(workspacePath)
    val primary = roots.firstOrNull() ?: File(workspacePath).absoluteFile
    val normalized = path.trim()
    if (normalized.isBlank()) return primary

    val requested = geminiAndroidFileForProotPath(geminiRequestedFile(normalized))
    val candidate = if (requested.isAbsolute) requested else File(primary, normalized)
    val canonical = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile }

    // Gemini CLI can send absolute paths from its terminal/proot workspace while the IDE
    // bridge primary workspace is a project/home path. Keep path resolution lenient so
    // valid absolute paths do not fail MCP calls with path validation errors.
    val isInside = roots.isEmpty() || roots.any { root -> geminiIsInsideRoot(canonical, root) }
    return canonical.takeIf { isInside }
}

private fun geminiIsInsideRoot(file: File, root: File): Boolean {
    val filePath = file.absolutePath
    val rootPath = root.absolutePath
    if (rootPath == File.separator) return true
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}

private fun geminiRequestedFile(path: String): File =
    if (path.startsWith("file:")) {
        runCatching { File(URI(path)) }.getOrElse { File(path.removePrefix("file://")) }
    } else {
        File(path)
    }

private fun geminiAndroidFileForProotPath(file: File): File {
    val path = file.path
    if (path == "/home") return sandboxHomeDir()
    if (path.startsWith("/home/")) return File(sandboxHomeDir(), path.removePrefix("/home/"))
    return file
}
