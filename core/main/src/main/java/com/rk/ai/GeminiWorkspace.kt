package com.rk.ai

import java.io.File
import java.net.URI

private val fallbackWorkspaceRoots = listOf("/", "/home", "/storage/emulated/0")

internal fun geminiIdeWorkspacePath(primary: String): String =
    (listOf(primary) + fallbackWorkspaceRoots)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(File.pathSeparator)

internal fun geminiWorkspaceRoots(workspacePath: String): List<File> =
    geminiIdeWorkspacePath(workspacePath)
        .split(File.pathSeparator)
        .mapNotNull { root -> runCatching { File(root).canonicalFile }.getOrNull() }
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

    val requested = geminiRequestedFile(normalized)
    val candidate = if (requested.isAbsolute) requested else File(primary, normalized)
    val canonical = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile }

    // Gemini CLI can send absolute paths from its terminal/proot workspace while the IDE
    // bridge primary workspace is a project/home path. The discovery file exposes a
    // multi-root workspace including "/", so keep the bridge lenient and never crash the
    // CLI with MCP -32602 for valid file paths.
    return canonical.takeIf { roots.isEmpty() || roots.any { root -> geminiIsInsideRoot(it, root) } } ?: canonical
}

private fun geminiIsInsideRoot(file: File, root: File): Boolean =
    root.path == File.separator || file.path == root.path || file.path.startsWith(root.path + File.separator)

private fun geminiRequestedFile(path: String): File =
    if (path.startsWith("file:")) {
        runCatching { File(URI(path)) }.getOrElse { File(path.removePrefix("file://")) }
    } else {
        File(path)
    }
