package com.rk.search.utils

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.search.CodeItem
import com.rk.settings.Settings
import com.rk.utils.hasBinaryChars
import com.rk.utils.isBinaryExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.Charset

object SearchUtils {
    private const val MAX_FILE_SIZE_SEARCH = 10_000_000 // 10 MB limit

    /**
     * Reads the file content, returning null if it's unsuitable for searching (e.g. if it's too large or likely
     * binary).
     *
     * @param file The file to read.
     * @return The file content as a [String], or null.
     */
    suspend fun isFileSearchable(file: FileObject): Boolean {
        // Do not search in file if it's over 10MB
        if (file.length() > MAX_FILE_SIZE_SEARCH) return false

        // Do not search in file if it's likely to be binary (file extension based detection)
        val ext = file.getExtension()
        if (isBinaryExtension(ext)) return false

        val charset = Charset.forName(Settings.encoding)

        // Do not search in file if it's likely to be binary (character based detection)
        val isBinary =
            withContext(Dispatchers.IO) {
                try {
                    file.useInputStream { stream ->
                        val buffer = CharArray(1024)
                        val charsRead = InputStreamReader(stream, charset).read(buffer, 0, buffer.size)
                        val sample = String(buffer, 0, charsRead)
                        hasBinaryChars(sample)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    true
                }
            }
        return !isBinary
    }

    fun findAllIndices(text: String, query: String, ignoreCase: Boolean): List<Int> {
        val indices = mutableListOf<Int>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val index = text.indexOf(query, currentIndex, ignoreCase)
            if (index == -1) break

            indices.add(index)
            currentIndex = index + query.length
        }

        return indices
    }

    suspend fun createCodeItem(
        context: Context,
        mainViewModel: MainViewModel,
        text: String,
        charIndex: Int,
        query: String,
        file: FileObject,
        projectRoot: FileObject,
        lineIndex: Int,
        isOpen: Boolean = false,
    ): CodeItem {
        val snippetResult =
            SnippetBuilder(context)
                .generateSnippet(
                    text = text,
                    highlight = Highlight(charIndex, charIndex + query.length),
                    fileExt = file.getExtension(),
                )

        val codeItem =
            CodeItem(
                snippet = snippetResult,
                file = file,
                line = lineIndex,
                column = charIndex,
                isOpen = isOpen,
                onClick = {
                    mainViewModel.viewModelScope.launch {
                        mainViewModel.editorManager.jumpToPosition(
                            file = file,
                            projectRoot = projectRoot,
                            lineStart = lineIndex,
                            charStart = charIndex,
                            lineEnd = lineIndex,
                            charEnd = charIndex + query.length,
                        )
                    }
                },
            )
        return codeItem
    }
}
