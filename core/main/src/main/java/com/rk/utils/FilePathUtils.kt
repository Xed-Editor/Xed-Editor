package com.rk.utils

import android.content.Intent
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.file.FileWrapper
import java.io.File
import kotlinx.coroutines.launch

data class PathMatch(
    val start: Int = 0,
    val end: Int = 0,
    val file: File,
    val line: Int? = null,
    val column: Int? = null,
)

object FilePathUtils {
    private val candidateRegex = Regex("""(?<!\w)(["'])(.+?)\1|(?:file://)?[^\s"'()\[\]{}<>,;:]+(?::\d+){0,2}""")
    private val lineColRegex = Regex(":(\\d+)(?::(\\d+))?$")

    fun detectFilePaths(text: String, projectRoot: File? = null): List<PathMatch> {
        if (text.isBlank()) return emptyList()
        val matches = mutableListOf<PathMatch>()

        for (match in candidateRegex.findAll(text)) {
            val quoted = match.groups[2]
            val rawCandidate = quoted?.value ?: match.value
            val matchStart = quoted?.range?.first ?: match.range.first
            val lineCol = lineColRegex.find(rawCandidate)
            val cleaned = rawCandidate.replace(lineColRegex, "")

            if (cleaned.length < 2) continue

            val file = resolveFile(cleaned, projectRoot)
            if (file != null) {
                val matchEnd = matchStart + cleaned.length

                if (matches.none { matchStart < it.end && matchEnd > it.start }) {
                    val line = lineCol?.groupValues?.get(1)?.toIntOrNull()
                    val column = lineCol?.groupValues?.get(2)?.toIntOrNull()

                    matches.add(
                        PathMatch(
                            start = matchStart,
                            end = matchEnd,
                            file = file,
                            line = line,
                            column = column,
                        )
                    )
                }
            }
        }
        return matches
    }

    fun resolveFile(path: String, projectRoot: File?): File? {
        val normalized = normalizeUriToPath(path)
        val isAbsolute = normalized.startsWith("/") || normalized == "~" || normalized.startsWith("~/")

        if (!isAbsolute && projectRoot != null) {
            val projectFile = File(projectRoot, normalized)
            if (projectFile.exists()) return projectFile
        } else {
            val hostFile = terminalToHostFile(normalized)
            if (hostFile.exists()) return hostFile
        }

        return null
    }

    fun parseFilePath(path: String, projectRoot: File? = null): PathMatch? {
        val lineCol = lineColRegex.find(path)
        val cleaned = path.replace(lineColRegex, "")

        if (cleaned.isBlank()) return null

        val line = lineCol?.groupValues?.get(1)?.toIntOrNull()
        val column = lineCol?.groupValues?.get(2)?.toIntOrNull()

        val normalized = normalizeUriToPath(cleaned)
        val isAbsolute = normalized.startsWith("/") || normalized == "~" || normalized.startsWith("~/")

        val file =
            if (!isAbsolute && projectRoot != null) {
                File(projectRoot, normalized)
            } else {
                terminalToHostFile(normalized)
            }

        val canonicalFile = runCatching { file.canonicalFile }.getOrDefault(file)

        return PathMatch(
            start = 0,
            end = path.length,
            file = canonicalFile,
            line = line,
            column = column,
        )
    }

    fun openPathMatch(patchMatch: PathMatch) {
        val mainActivity = MainActivity.instance ?: return
        val file = patchMatch.file

        if (file.isDirectory) {
            mainActivity.drawerViewModel.addFileTreeTab(FileWrapper(file), true)
        } else {
            mainActivity.viewModel.editorManager.let { editorManager ->
                DefaultScope.launch {
                    if (patchMatch.line != null) {
                        val lineIndex = (patchMatch.line - 1).coerceAtLeast(0)
                        val columnIndex = patchMatch.column?.minus(1)?.coerceAtLeast(0) ?: 0
                        editorManager.jumpToPosition(
                            FileWrapper(file),
                            projectRoot = null,
                            lineStart = lineIndex,
                            charStart = columnIndex,
                        )
                    } else {
                        editorManager.openFile(FileWrapper(file), projectRoot = null)
                    }
                }
            }
        }

        val intent =
            Intent(mainActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        mainActivity.startActivity(intent)
    }
}
