package com.rk.ai.tools

import com.rk.activities.main.MainActivity
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.resolve
import com.rk.file.resolveOrCreateDirectory
import java.io.File
import java.io.IOException

object AiWorkspace {
    fun root(): FileObject {
        val activity = MainActivity.instance
        activity?.viewModel?.currentProjectRoot?.let { return it }
        val dir = activity?.getExternalFilesDir(null) ?: activity?.filesDir
        return FileWrapper(dir ?: File("/"))
    }

    fun nativeRootPath(): String? = root().nativePathOrNull()

    suspend fun resolve(path: String): FileObject {
        val segments = segmentsOf(path)
        val base = root()
        if (segments.isEmpty()) return base
        return base.resolve(segments.joinToString("/"))
            ?: throw IllegalArgumentException("Not found: $path")
    }

    suspend fun resolveForWrite(path: String): FileObject {
        val segments = segmentsOf(path)
        require(segments.isNotEmpty()) { "A file path is required" }

        val base = root()
        val parent =
            if (segments.size == 1) {
                base
            } else {
                base.resolveOrCreateDirectory(segments.dropLast(1).joinToString("/"))
            }

        val name = segments.last()
        return parent.getChild(name)
            ?: parent.createChild(createFile = true, name = name)
            ?: throw IOException("Cannot create $path")
    }

    suspend fun createDirectory(path: String): FileObject {
        val segments = segmentsOf(path)
        require(segments.isNotEmpty()) { "A directory path is required" }
        return root().resolveOrCreateDirectory(segments.joinToString("/"))
    }

    suspend fun destination(path: String): Pair<FileObject, String> {
        val segments = segmentsOf(path)
        require(segments.isNotEmpty()) { "A destination path is required" }
        val base = root()
        val parent =
            if (segments.size == 1) {
                base
            } else {
                base.resolveOrCreateDirectory(segments.dropLast(1).joinToString("/"))
            }
        return parent to segments.last()
    }

    fun relativize(file: FileObject): String {
        val base = root().getAbsolutePath().trimEnd('/')
        val absolute = file.getAbsolutePath()
        return if (absolute.startsWith("$base/")) absolute.removePrefix("$base/") else absolute
    }

    fun normalizePath(path: String): String = segmentsOf(path).joinToString("/")

    private fun segmentsOf(path: String): List<String> {
        val segments =
            path.trim()
                .replace('\\', '/')
                .removePrefix("/")
                .split('/')
                .filter { it.isNotBlank() && it != "." }
        require(segments.none { it == ".." }) { "Path escapes the workspace: $path" }
        return segments
    }
}
