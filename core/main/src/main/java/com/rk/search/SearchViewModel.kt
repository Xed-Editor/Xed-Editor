package com.rk.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.file.toFileWrapper
import com.rk.settings.Settings
import com.rk.settings.editor.LineEnding
import com.rk.tabs.editor.EditorTab
import com.rk.utils.hasBinaryChars
import com.rk.utils.isBinaryExtension
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    private var isIndexing = mutableStateMapOf<FileObject, Boolean>()

    // File search dialog
    var fileSearchQuery by mutableStateOf("")

    // Code search dialog
    var codeSearchQuery by mutableStateOf("")
    var codeReplaceQuery by mutableStateOf("")
    var showOptionsMenu by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var searchRegex by mutableStateOf(false)
    var searchWholeWord by mutableStateOf(false)
    var isReplaceShown by mutableStateOf(false)
        private set

    companion object {
        private const val MAX_CHUNK_SIZE = 1_000_000 // 1 MB limit per column to avoid CursorWindow crash
    }

    fun toggleReplaceShown() {
        isReplaceShown = !isReplaceShown
    }

    suspend fun replaceIn(mainViewModel: MainViewModel, codeItem: CodeItem, onFinished: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            val lineIndex = codeItem.line
            val startCol = codeItem.column
            val endCol = codeItem.column + codeSearchQuery.length

            if (codeItem.isOpen) {
                val tab =
                    mainViewModel.tabs.filterIsInstance<EditorTab>().find { tab -> tab.file == codeItem.file }
                        ?: return@withContext
                val editor = tab.editorState.editor.get() ?: return@withContext

                withContext(Dispatchers.Main) {
                    editor.text.replace(lineIndex, startCol, lineIndex, endCol, codeReplaceQuery)
                }
            } else {
                val content = codeItem.file.readText() ?: return@withContext
                val lines = content.lines().toMutableList()

                val line = lines.getOrNull(lineIndex) ?: return@withContext
                val newLine = line.replaceRange(startCol, endCol, codeReplaceQuery)
                lines[lineIndex] = newLine

                val charset = Charset.forName(Settings.encoding)
                val lineEnding = LineEnding.detect(content)

                val normalizedContent = lines.joinToString(lineEnding.char)
                codeItem.file.writeText(normalizedContent, charset)
            }

            onFinished()
        }
    }

    fun isIndexing(projectRoot: FileObject): Boolean {
        return isIndexing[projectRoot] ?: false
    }

    data class IndexingStats(val totalFiles: Int, val databaseSize: Long)

    suspend fun getStats(context: Context, projectRoot: FileObject): IndexingStats {
        val totalFiles = getDatabase(context, projectRoot).fileMetaDao().getAll().size
        val databaseSize = IndexDatabase.getDatabaseSize(context, projectRoot)
        return IndexingStats(totalFiles, databaseSize)
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

    private fun findAllIndices(text: String, query: String, ignoreCase: Boolean = true): List<Int> {
        val indices = mutableListOf<Int>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val index = text.indexOf(query, currentIndex, ignoreCase)
            if (index == -1) break

            indices.add(index)
            currentIndex = index + 1
        }

        return indices
    }

    fun searchCode(
        context: Context,
        mainViewModel: MainViewModel,
        projectRoot: FileObject,
        query: String,
        useIndex: Boolean = true,
    ): Flow<CodeItem> = channelFlow {
        // Search in opened tabs
        val openedEditorTabs = mainViewModel.tabs.mapNotNull { it as? EditorTab }
        val excludedFiles = openedEditorTabs.map { it.file.getAbsolutePath() }.toSet()
        for (tab in openedEditorTabs) {
            val editor = tab.editorState.editor.get()
            editor?.text.toString().lines().forEachIndexed { lineIndex, line ->
                val indices = findAllIndices(line, query, ignoreCase = true)
                for (index in indices) {
                    currentCoroutineContext().ensureActive()
                    send(
                        createCodeItem(
                            context = context,
                            mainViewModel = mainViewModel,
                            text = line,
                            charIndex = index,
                            query = query,
                            file = tab.file,
                            lineIndex = lineIndex,
                            isOpen = true,
                        )
                    )
                }
            }
        }

        // Search through other files
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

                val indices = findAllIndices(result.content, query, ignoreCase = true)
                for (index in indices) {
                    val absoluteCharIndex = result.chunkStart + index
                    val file = File(result.path).toFileWrapper()

                    currentCoroutineContext().ensureActive()
                    send(
                        createCodeItem(
                            context = context,
                            mainViewModel = mainViewModel,
                            text = result.content,
                            charIndex = absoluteCharIndex,
                            query = query,
                            file = file,
                            lineIndex = result.lineNumber,
                        )
                    )
                }
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
                val chunks = line.chunked(MAX_CHUNK_SIZE)
                chunks.forEachIndexed { chunkIndex, chunk ->
                    val indices = findAllIndices(chunk, query, ignoreCase = true)
                    for (index in indices) {
                        val absoluteCharIndex = (chunkIndex * MAX_CHUNK_SIZE) + index
                        currentCoroutineContext().ensureActive()
                        send(
                            createCodeItem(
                                context = context,
                                mainViewModel = mainViewModel,
                                text = chunk,
                                charIndex = absoluteCharIndex,
                                query = query,
                                file = file,
                                lineIndex = lineIndex,
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun createCodeItem(
        context: Context,
        mainViewModel: MainViewModel,
        text: String,
        charIndex: Int,
        query: String,
        file: FileObject,
        lineIndex: Int,
        isOpen: Boolean = false,
    ): CodeItem {
        val snippetResult =
            SnippetBuilder(context)
                .generateSnippet(
                    text = text,
                    highlight = Highlight(charIndex, charIndex + query.length),
                    fileExt = file.getName().substringAfterLast(".", ""),
                )

        val codeItem =
            CodeItem(
                snippet = snippetResult,
                file = file,
                line = lineIndex,
                column = charIndex,
                isOpen = isOpen,
                onClick = {
                    viewModelScope.launch {
                        mainViewModel.goToTabAndSelect(
                            file = file,
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

    suspend fun index(context: Context, projectRoot: FileObject) {
        isIndexing[projectRoot] = true

        val database = getDatabase(context, projectRoot)
        val codeLineDao = database.codeIndexDao()
        val fileMetaDao = database.fileMetaDao()

        val indexedFiles = fileMetaDao.getAll().associateBy { it.path }
        val pathsToKeep = mutableSetOf<String>()

        val newCodeLines = mutableListOf<CodeLine>()
        val newFileMetas = mutableListOf<FileMeta>()

        try {
            indexRecursively(projectRoot, indexedFiles, pathsToKeep, newCodeLines, newFileMetas)
            updateIndex(database, indexedFiles, pathsToKeep, codeLineDao, fileMetaDao, newCodeLines, newFileMetas)
        } finally {
            isIndexing[projectRoot] = false
        }
    }

    fun syncIndex(file: FileObject) {
        viewModelScope.launch(Dispatchers.IO) {
            val databases = IndexDatabase.findDatabasesFor(file)
            for (database in databases) {
                isIndexing[database.projectRoot] = true

                val codeLineDao = database.codeIndexDao()
                val fileMetaDao = database.fileMetaDao()

                val indexedFiles = fileMetaDao.getAll().associateBy { it.path }
                val pathsToKeep = mutableSetOf<String>()

                val newCodeLines = mutableListOf<CodeLine>()
                val newFileMetas = mutableListOf<FileMeta>()

                try {
                    if (file == database.projectRoot) {
                        indexRecursively(file, indexedFiles, pathsToKeep, newCodeLines, newFileMetas)
                    } else {
                        indexFile(file, indexedFiles, pathsToKeep, newCodeLines, newFileMetas)
                    }

                    updateIndex(
                        database,
                        indexedFiles.filter { it.key.startsWith(file.getAbsolutePath()) },
                        pathsToKeep,
                        codeLineDao,
                        fileMetaDao,
                        newCodeLines,
                        newFileMetas,
                    )
                } finally {
                    isIndexing[database.projectRoot] = false
                }
            }
        }
    }

    fun deleteIndex(context: Context, projectRoot: FileObject) {
        IndexDatabase.removeDatabase(context, projectRoot)
    }

    private suspend fun updateIndex(
        database: IndexDatabase,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineDao: CodeLineDao,
        fileMetaDao: FileMetaDao,
        newCodeLines: MutableList<CodeLine>,
        newFileMetas: MutableList<FileMeta>,
    ) {
        database.withTransaction {
            val deletedPaths = indexedFiles.keys - pathsToKeep
            deletedPaths.forEach { path ->
                codeLineDao.deleteByPath(path)
                fileMetaDao.deleteByPath(path)
            }

            codeLineDao.insertAll(newCodeLines)
            fileMetaDao.insertAll(newFileMetas)
        }
    }

    private suspend fun indexRecursively(
        parent: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        isResultHidden: Boolean = false,
    ) {
        val childFiles = parent.listFiles()

        for (file in childFiles) {
            indexFile(file, indexedFiles, pathsToKeep, codeLineResults, fileMetaResults, isResultHidden)
        }
    }

    private suspend fun indexFile(
        file: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        isResultHidden: Boolean = false,
    ) {
        val isHidden = file.getName().startsWith(".") || isResultHidden
        if (isHidden && !Settings.show_hidden_files_search) return

        val path = file.getAbsolutePath()
        val lastModified = file.lastModified()

        val indexedFile = indexedFiles[path]
        val isFileModified =
            indexedFile == null || indexedFile.lastModified != lastModified || indexedFile.size != file.length()
        if (!isFileModified) {
            pathsToKeep += path
            if (!file.isDirectory()) return
        } else {
            fileMetaResults.add(
                FileMeta(path = path, fileName = file.getName(), lastModified = lastModified, size = file.length())
            )
        }

        if (file.isDirectory()) {
            indexRecursively(
                parent = file,
                indexedFiles = indexedFiles,
                pathsToKeep = pathsToKeep,
                codeLineResults = codeLineResults,
                fileMetaResults = fileMetaResults,
                isResultHidden = isHidden,
            )
            return
        }

        val fileText = getFileContentOrNull(file) ?: return

        val lines = fileText.lines()
        lines.forEachIndexed { lineIndex, line ->
            val chunks = line.chunked(MAX_CHUNK_SIZE)
            chunks.forEachIndexed { chunkIndex, chunk ->
                codeLineResults.add(
                    CodeLine(
                        content = chunk,
                        path = path,
                        lineNumber = lineIndex,
                        chunkStart = chunkIndex * MAX_CHUNK_SIZE,
                    )
                )
            }
        }
    }
}
