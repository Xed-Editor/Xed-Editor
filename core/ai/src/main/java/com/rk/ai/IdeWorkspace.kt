package com.rk.ai

import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.tabs.editor.EditorTab
import java.io.File
import java.net.URI

internal fun ideWorkspacePath(primary: String): String {
    val primaryFile = File(primary)
    val canonicalPrimary = runCatching { primaryFile.canonicalPath }.getOrDefault(primaryFile.absolutePath)
    val variants = mutableListOf(canonicalPrimary)
    
    // Always provide both /storage/emulated/0 and /sdcard variants for Android compatibility
    if (canonicalPrimary.startsWith("/storage/emulated/0")) {
        variants.add(canonicalPrimary.replace("/storage/emulated/0", "/sdcard"))
    } else if (canonicalPrimary.startsWith("/sdcard")) {
        variants.add(canonicalPrimary.replace("/sdcard", "/storage/emulated/0"))
    }

    val roots = if (com.rk.settings.Settings.sandbox) {
        (variants + "/home").filter { it.isNotBlank() }
    } else {
        (variants + AiConfig.fallbackWorkspaceRoots).filter { it.isNotBlank() }
    }

    return roots
        .distinct()
        .joinToString(File.pathSeparator)
}

internal fun workspaceRoots(workspacePath: String): List<File> =
    ideWorkspacePath(workspacePath)
        .split(File.pathSeparator)
        .mapNotNull { root -> runCatching { androidFileForProotPath(File(root)).canonicalFile }.getOrNull() }
        .distinctBy { it.path }

internal fun displayRootFor(workspacePath: String, file: File): File =
    workspaceRoots(workspacePath)
        .filter { root -> isInsideRoot(file, root) && root.path != File.separator }
        .maxByOrNull { it.path.length }
        ?: workspaceRoots(workspacePath).firstOrNull()
        ?: file

internal fun resolveWorkspacePath(workspacePath: String, path: String): File? {
    val roots = workspaceRoots(workspacePath)
    val primary = roots.firstOrNull() ?: File(workspacePath).absoluteFile
    val normalized = path.trim()
    if (normalized.isBlank()) return primary

    val requested = androidFileForProotPath(requestedFile(normalized))
    val candidate = if (requested.isAbsolute) requested else File(primary, normalized)
    val canonical = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile }

    // Gemini CLI can send absolute paths from its terminal/proot workspace while the IDE
    // bridge primary workspace is a project/home path. Keep path resolution lenient so
    // valid absolute paths do not fail MCP calls with path validation errors.
    val isInside = roots.isEmpty() || roots.any { root -> isInsideRoot(canonical, root) }
    return canonical.takeIf { isInside }
}

private fun isInsideRoot(file: File, root: File): Boolean {
    val filePath = file.absolutePath
    val rootPath = root.absolutePath
    if (rootPath == File.separator) return true
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}

private fun requestedFile(path: String): File =
    if (path.startsWith("file:")) {
        runCatching { File(URI(path)) }.getOrElse { File(path.removePrefix("file://")) }
    } else {
        File(path)
    }

private fun androidFileForProotPath(file: File): File {
    val path = file.path
    if (path == "/home") return sandboxHomeDir()
    if (path.startsWith("/home/")) return File(sandboxHomeDir(), path.removePrefix("/home/"))
    return file
}

suspend fun workingDirFor(file: FileObject, projectRoot: FileObject?): String =
    projectRoot?.getAbsolutePath()
        ?: (file.getParentFile() as? FileWrapper)?.getAbsolutePath()
        ?: file.getAbsolutePath()

fun resolveRelativePathFromOpenEditor(path: String, tabs: com.rk.ai.service.TabRepository): File? {
    val activeTab = tabs.currentTab as? EditorTab
    val activeBase = activeTab?.let { File(it.file.getAbsolutePath()).parentFile }
    activeBase?.let { parent ->
        resolveWorkspacePath(com.rk.ai.IdeBridge.workspacePathForResolution(), File(parent, path).path)?.let { return it }
    }
    val exactMatches = tabs.tabs
        .filterIsInstance<EditorTab>()
        .mapNotNull { tab ->
            val tabFile = File(tab.file.getAbsolutePath())
            if (tabFile.path.endsWith(File.separator + path)) {
                resolveWorkspacePath(com.rk.ai.IdeBridge.workspacePathForResolution(), tabFile.path)
            } else null
        }
        .distinctBy { it.absolutePath }
    if (exactMatches.size == 1) return exactMatches.first()
    return null
}
