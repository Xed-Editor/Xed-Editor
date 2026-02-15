package com.rk.search

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.file.toFileWrapper
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.hasBinaryChars
import com.rk.utils.isBinaryExtension
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    private var isIndexing = mutableStateMapOf<FileObject, Boolean>()

    fun isIndexing(projectRoot: FileObject): Boolean {
        return isIndexing[projectRoot] ?: false
    }

    private fun getDatabase(context: Context, projectRoot: FileObject): IndexDatabase {
        return IndexDatabase.getDatabase(context, projectRoot)
    }

    suspend fun searchFileName(
        context: Context,
        projectRoot: FileObject,
        query: String,
        useIndex: Boolean = true,
    ): List<FileMeta> {
        return if (useIndex) {
            searchFileNameWithIndex(context, projectRoot, query)
        } else {
            searchFileNameWithoutIndex(projectRoot, query)
        }
    }

    private suspend fun searchFileNameWithIndex(
        context: Context,
        projectRoot: FileObject,
        query: String,
    ): List<FileMeta> = getDatabase(context, projectRoot).fileMetaDao().search(query)

    private suspend fun searchFileNameWithoutIndex(projectRoot: FileObject, query: String): List<FileMeta> {
        val results = mutableListOf<FileMeta>()

        suspend fun searchRecursively(parent: FileObject, results: MutableList<FileMeta>) {
            val childFiles = parent.listFiles()

            for (file in childFiles) {
                val isHidden = file.getName().startsWith(".")
                if (isHidden && !Settings.show_hidden_files_search) continue

                if (file.getName().contains(query, ignoreCase = true)) {
                    results.add(
                        // Last modified and size do not matter here, as they're only used for indexing
                        FileMeta(path = file.getAbsolutePath(), fileName = file.getName(), lastModified = 0, size = 0)
                    )
                }

                if (file.isDirectory()) {
                    searchRecursively(file, results)
                }
            }
        }

        searchRecursively(projectRoot, results)
        return results
    }

    fun searchCode(
        context: Context,
        mainViewModel: MainViewModel,
        projectRoot: FileObject,
        query: String,
        useIndex: Boolean = true,
    ): Flow<CodeItem> = channelFlow {
        val openedEditorTabs = mainViewModel.tabs.mapNotNull { it as? EditorTab }
        val excludedFiles = openedEditorTabs.map { it.file.getAbsolutePath() }.toSet()
        for (tab in openedEditorTabs) {
            val editor = tab.editorState.editor.get()
            editor?.text.toString().lines().forEachIndexed { lineIndex, line ->
                val charIndex = line.indexOf(query, ignoreCase = true)
                if (charIndex == -1) return@forEachIndexed

                val snippet =
                    SnippetBuilder(context)
                        .generateSnippet(
                            text = line,
                            highlightStart = charIndex,
                            highlightEnd = charIndex + query.length,
                            fileExt = tab.file.getName().substringAfterLast("."),
                        )

                val codeItem =
                    CodeItem(
                        snippet = snippet,
                        file = tab.file,
                        line = lineIndex,
                        column = charIndex,
                        opened = true,
                        onClick = {
                            viewModelScope.launch {
                                mainViewModel.goToTabAndSelect(
                                    file = tab.file,
                                    lineStart = lineIndex,
                                    charStart = charIndex,
                                    lineEnd = lineIndex,
                                    charEnd = charIndex + query.length,
                                )
                            }
                        },
                    )
                send(codeItem)
            }
        }

        if (!useIndex) {
            searchCodeWithoutIndex(
                context = context,
                mainViewModel = mainViewModel,
                parent = projectRoot,
                query = query,
                excludedFiles = excludedFiles,
                send = ::send,
            )
        } else {
            searchCodeWithIndex(
                context = context,
                mainViewModel = mainViewModel,
                projectRoot = projectRoot,
                query = query,
                excludedFiles = excludedFiles,
                send = ::send,
            )
        }
    }

    private suspend fun searchCodeWithIndex(
        context: Context,
        mainViewModel: MainViewModel,
        projectRoot: FileObject,
        query: String,
        excludedFiles: Set<String>,
        send: suspend (CodeItem) -> Unit,
    ) {
        var resultLimit = 5
        var offset = 0

        val dao = getDatabase(context, projectRoot).codeIndexDao()

        while (true) {
            val results = dao.search(query, resultLimit, offset)
            if (results.isEmpty()) break

            for (result in results) {
                if (result.path in excludedFiles) continue
                val charIndex = result.content.indexOf(query, ignoreCase = true)
                val absoluteCharIndex = result.chunkStart + charIndex

                val file = File(result.path).toFileWrapper()
                val snippet =
                    SnippetBuilder(context)
                        .generateSnippet(
                            text = result.content,
                            highlightStart = absoluteCharIndex,
                            highlightEnd = absoluteCharIndex + query.length,
                            fileExt = file.getName().substringAfterLast("."),
                        )

                val codeItem =
                    CodeItem(
                        snippet = snippet,
                        file = file,
                        line = result.lineNumber,
                        column = absoluteCharIndex,
                        onClick = {
                            viewModelScope.launch {
                                mainViewModel.goToTabAndSelect(
                                    file = file,
                                    lineStart = result.lineNumber,
                                    charStart = absoluteCharIndex,
                                    lineEnd = result.lineNumber,
                                    charEnd = absoluteCharIndex + query.length,
                                )
                            }
                        },
                    )
                send(codeItem)
            }
            offset += resultLimit
            resultLimit = 20
        }
    }

    private suspend fun searchCodeWithoutIndex(
        context: Context,
        mainViewModel: MainViewModel,
        parent: FileObject,
        query: String,
        excludedFiles: Set<String>,
        send: suspend (CodeItem) -> Unit,
        isResultHidden: Boolean = false,
    ) {
        val childFiles = parent.listFiles()

        for (file in childFiles) {
            if (file.getAbsolutePath() in excludedFiles) continue

            val isHidden = file.getName().startsWith(".") || isResultHidden
            if (isHidden && !Settings.show_hidden_files_search) continue

            if (file.isDirectory()) {
                searchCodeWithoutIndex(context, mainViewModel, file, query, excludedFiles, send, isResultHidden)
                continue
            }

            val fileText = getFileContentOrNull(file) ?: continue

            val lines = fileText.lines()
            lines.forEachIndexed { lineIndex, line ->
                val maxLength = 1_000_000 // 1 MB limit per column to avoid CursorWindow crash
                val chunks = line.chunked(maxLength)
                chunks.forEachIndexed { chunkIndex, chunk ->
                    val charIndex = chunk.indexOf(query, ignoreCase = true)
                    if (charIndex == -1) return@forEachIndexed
                    val absoluteCharIndex = (chunkIndex * maxLength) + charIndex

                    val snippet =
                        SnippetBuilder(context)
                            .generateSnippet(
                                text = chunk,
                                highlightStart = absoluteCharIndex,
                                highlightEnd = absoluteCharIndex + query.length,
                                fileExt = file.getName().substringAfterLast(".", ""),
                            )

                    val codeItem =
                        CodeItem(
                            snippet = snippet,
                            file = file,
                            line = lineIndex,
                            column = absoluteCharIndex,
                            onClick = {
                                viewModelScope.launch {
                                    mainViewModel.goToTabAndSelect(
                                        file = file,
                                        lineStart = lineIndex,
                                        charStart = absoluteCharIndex,
                                        lineEnd = lineIndex,
                                        charEnd = absoluteCharIndex + query.length,
                                    )
                                }
                            },
                        )
                    send(codeItem)
                }
            }
        }
    }

    /**
     * Reads the file content, returning null if it's unsuitable for searching (e.g. if it's too large or likely
     * binary).
     *
     * @param file The file to read.
     * @return The file content as a [String], or null.
     */
    private suspend fun getFileContentOrNull(file: FileObject): String? {
        // Do not search in file if it's over 10MB
        if (file.length() > 10_000_000) return null

        // Do not search in file if it's likely to be binary (file extension based detection)
        val ext = file.getName().substringAfterLast(".", "")
        if (isBinaryExtension(ext)) return null

        val fileText =
            withContext(Dispatchers.IO) {
                try {
                    file.readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        // Do not search in file if it's likely to be binary (character based detection)
        if (fileText == null || hasBinaryChars(fileText)) return null

        return fileText
    }

    suspend fun index(context: Context, mainViewModel: MainViewModel, projectRoot: FileObject) {
        val database = getDatabase(context, projectRoot)
        val codeLineDao = database.codeIndexDao()
        val fileMetaDao = database.fileMetaDao()

        val indexedFiles = fileMetaDao.getAll().associateBy { it.path }
        val pathsToKeep = mutableSetOf<String>()

        val newCodeLines = mutableListOf<CodeLine>()
        val newFileMetas = mutableListOf<FileMeta>()

        try {
            isIndexing[projectRoot] = true
            indexRecursively(mainViewModel, projectRoot, indexedFiles, pathsToKeep, newCodeLines, newFileMetas)

            database.withTransaction {
                val deletedPaths = indexedFiles.keys - pathsToKeep
                deletedPaths.forEach { path ->
                    codeLineDao.deleteByPath(path)
                    fileMetaDao.deleteByPath(path)
                }

                codeLineDao.insertAll(newCodeLines)
                fileMetaDao.insertAll(newFileMetas)
            }
        } finally {
            isIndexing[projectRoot] = false
        }
    }

    fun deleteIndex(context: Context, projectRoot: FileObject) {
        IndexDatabase.removeDatabase(context, projectRoot)
    }

    private suspend fun indexRecursively(
        mainViewModel: MainViewModel,
        parent: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        isResultHidden: Boolean = false,
    ) {
        val childFiles = parent.listFiles()

        for (file in childFiles) {
            val isHidden = file.getName().startsWith(".") || isResultHidden
            if (isHidden && !Settings.show_hidden_files_search) continue

            val path = file.getAbsolutePath()
            val lastModified = file.lastModified()

            val indexedFile = indexedFiles[path]
            val isFileModified =
                indexedFile == null || indexedFile.lastModified != lastModified || indexedFile.size != file.length()
            if (!isFileModified) {
                pathsToKeep += path
                if (!file.isDirectory()) continue
            } else {
                fileMetaResults.add(
                    FileMeta(path = path, fileName = file.getName(), lastModified = lastModified, size = file.length())
                )
            }

            if (file.isDirectory()) {
                indexRecursively(
                    mainViewModel = mainViewModel,
                    parent = file,
                    indexedFiles = indexedFiles,
                    pathsToKeep = pathsToKeep,
                    codeLineResults = codeLineResults,
                    fileMetaResults = fileMetaResults,
                    isResultHidden = isHidden,
                )
                continue
            }

            val fileText = getFileContentOrNull(file) ?: continue

            val lines = fileText.lines()
            lines.forEachIndexed { lineIndex, line ->
                val maxLength = 1_000_000 // 1 MB limit per column to avoid CursorWindow crash
                val chunks = line.chunked(maxLength)
                chunks.forEachIndexed { chunkIndex, chunk ->
                    codeLineResults.add(
                        CodeLine(
                            content = chunk,
                            path = path,
                            lineNumber = lineIndex,
                            chunkStart = chunkIndex * maxLength,
                        )
                    )
                }
            }
        }
    }
}
