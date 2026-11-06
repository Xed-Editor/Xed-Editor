package com.rk.utils

import android.content.Intent
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import java.io.File
import kotlinx.coroutines.launch

data class PathMatch(
    val start: Int,
    val end: Int,
    val file: File,
)

object FilePathUtils {
    private val candidateRegex = Regex("""(?:\b[a-zA-Z]:[/\\]|\B[/~]|\b)[^\s"'()\[\]{}<>,;:]+""")

    fun detectFilePaths(text: String, projectRoot: File? = null): List<PathMatch> {
        if (text.isBlank()) return emptyList()
        val matches = mutableListOf<PathMatch>()

        for (match in candidateRegex.findAll(text)) {
            val rawCandidate = match.value
            val cleaned =
                rawCandidate
                    .replace(Regex(":\\d+(?::\\d+)?$"), "")
                    .trim('\'', '"', '(', ')', '[', ']', '{', '}', '<', '>', ',', ';', ':', '.')

            if (cleaned.length < 2) continue

            val file = resolveFile(cleaned, projectRoot)
            if (file != null) {
                val matchStart = match.range.first
                val matchEnd = match.range.first + cleaned.length

                if (matches.none { matchStart < it.end && matchEnd > it.start }) {
                    matches.add(
                        PathMatch(
                            start = matchStart,
                            end = matchEnd,
                            file = file,
                        )
                    )
                }
            }
        }
        return matches
    }

    fun resolveFile(path: String, projectRoot: File?): File? {
        if (path.isBlank()) return null

        var expandedPath = path
        if (expandedPath.startsWith("~")) {
            val homePath = sandboxHomeDir().absolutePath
            expandedPath = expandedPath.replaceFirst("~", homePath)
        }

        val fileCandidate = File(expandedPath)
        if (fileCandidate.exists()) return fileCandidate

        if (!fileCandidate.isAbsolute && projectRoot != null) {
            val projectFile = File(projectRoot, expandedPath)
            if (projectFile.exists()) return projectFile
        }

        return null
    }

    fun openPath(file: File) {
        val mainActivity = MainActivity.instance ?: return

        if (file.isFile) {
            mainActivity.viewModel.editorManager.let { editorManager ->
                DefaultScope.launch { editorManager.openFile(FileWrapper(file), projectRoot = null) }
            }
        } else if (file.isDirectory) {
            mainActivity.drawerViewModel.addFileTreeTab(FileWrapper(file), true)
        }

        val intent =
            Intent(mainActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        mainActivity.startActivity(intent)
    }
}
