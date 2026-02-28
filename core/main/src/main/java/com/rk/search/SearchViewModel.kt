package com.rk.search

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.file.toFileWrapper
import com.rk.settings.Preference
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.settings.editor.LineEnding
import com.rk.tabs.editor.EditorTab
import com.rk.utils.hasBinaryChars
import com.rk.utils.isBinaryExtension
import com.rk.utils.parseExtensions
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    private var isIndexing = mutableStateMapOf<FileObject, Boolean>()
    private var indexJob: Job? = null

    // File search dialog
    var fileSearchQuery by mutableStateOf("")
    var isSearchingFiles by mutableStateOf(false)
    var fileSearchResults by mutableStateOf<List<FileMeta>>(emptyList())
    private var fileSearchJob: Job? = null

    // Code search dialog
    var showFileMaskDialog by mutableStateOf(false)
    var fileMaskText by mutableStateOf(Settings.file_mask)
    var fileMask = derivedStateOf { parseExtensions(fileMaskText) }
    private val excluder by derivedStateOf { GlobExcluder(ReactiveSettings.excludedFilesSearch) }

    var isSearchingCode by mutableStateOf(false)
    val codeSearchResults = mutableStateListOf<CodeItem>()
    val groupedCodeResults by derivedStateOf { codeSearchResults.groupBy { it.file } }
    private var codeSearchJob: Job? = null

    var codeSearchQuery by mutableStateOf("")
    var codeReplaceQuery by mutableStateOf("")
    var showOptionsMenu by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var isReplaceShown by mutableStateOf(false)
        private set

    companion object {
        private const val MAX_CHUNK_SIZE = 1_000_000 // 1 MB limit per column to avoid CursorWindow crash
    }

    fun cleanupJobs(projectRoot: FileObject) {
        fileSearchJob?.cancel()
        fileSearchJob = null

        codeSearchJob?.cancel()
        codeSearchJob = null

        indexJob?.cancel()
        indexJob = null
        isIndexing.remove(projectRoot)
    }

    fun matchesFileMask(fileExt: String): Boolean {
        if (fileMask.value.isEmpty()) return true
        return fileMask.value.any { it == fileExt }
    }

    fun cancelFileSearch() {
        fileSearchJob?.cancel()
        fileSearchJob = null
        isSearchingFiles = false
    }

    fun launchFileSearch(context: Context, projectRoot: FileObject) {
        cancelFileSearch()

        isSearchingFiles = true
        fileSearchJob =
            viewModelScope.launch {
                fileSearchResults =
                    searchFileName(
                        context = context,
                        projectRoot = projectRoot,
                        query = fileSearchQuery,
                        useIndex =
                            Preference.getBoolean(
                                "enable_indexing_${projectRoot.hashCode()}",
                                Settings.always_index_projects,
                            ),
                    )
                isSearchingFiles = false
            }
    }

    /** Cancels any running search */
    fun cancelCodeSearch() {
        codeSearchJob?.cancel()
        codeSearchJob = null

        codeSearchResults.clear()
        isSearchingCode = false
    }

    /** Executes a search */
    fun launchCodeSearch(context: Context, mainViewModel: MainViewModel, projectRoot: FileObject) {
        cancelCodeSearch()

        if (codeSearchQuery.isBlank()) {
            codeSearchResults.clear()
            return
        }

        codeSearchJob =
            viewModelScope.launch {
                isSearchingCode = true

                searchCode(
                        context = context,
                        projectRoot = projectRoot,
                        query = codeSearchQuery,
                        mainViewModel = mainViewModel,
                        useIndex =
                            Preference.getBoolean(
                                "enable_indexing_${projectRoot.hashCode()}",
                                Settings.always_index_projects,
                            ),
                    )
                    .collect { codeSearchResults.add(it) }

                isSearchingCode = false
            }
    }

    fun toggleReplaceShown() {
        isReplaceShown = !isReplaceShown
    }

    suspend fun replaceIn(mainViewModel: MainViewModel, codeItem: CodeItem) {
        withContext(Dispatchers.IO) {
            val lineIndex = codeItem.line
            val startCol = codeItem.column
            val diff = codeItem.snippet.highlight.endIndex - codeItem.snippet.highlight.startIndex
            val endCol = codeItem.column + diff

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
        }
    }

    fun isIndexing(projectRoot: FileObject): Boolean {
        return isIndexing[projectRoot] ?: false
    }

    data class IndexingStats(val totalFiles: Int, val databaseSize: Long)

    suspend fun getStats(context: Context, projectRoot: FileObject): IndexingStats {
        val totalFiles = getDatabase(context, projectRoot).fileMetaDao().getCount()
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
                val path = file.getAbsolutePath()
                if (excluder.isExcluded(path)) continue

                val isHidden = file.getName().startsWith(".")
                if (isHidden && !Settings.show_hidden_files_search) continue

                if (file.getName().contains(query, ignoreCase = true)) {
                    results.add(
                        // Last modified and size do not matter here, as they're only used for indexing
                        FileMeta(path = path, fileName = file.getName(), lastModified = 0, size = 0)
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

    private fun findAllIndices(text: String, query: String, ignoreCase: Boolean): List<Int> {
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
    ): Flow<CodeItem> = flow {
        // Search in opened tabs
        val openedEditorTabs = mainViewModel.tabs.mapNotNull { it as? EditorTab }
        val openPaths = openedEditorTabs.map { it.file.getAbsolutePath() }.toSet()

        for (tab in openedEditorTabs) {
            val fileExt = tab.file.getExtension()
            if (!matchesFileMask(fileExt)) continue

            val editor = tab.editorState.editor.get()
            val editorText = editor?.text.toString()
            editorText.lines().forEachIndexed { lineIndex, line ->
                val indices = findAllIndices(line, query, ignoreCase = ignoreCase)
                for (index in indices) {
                    currentCoroutineContext().ensureActive()
                    emit(
                        createCodeItem(
                            context = context,
                            mainViewModel = mainViewModel,
                            text = line,
                            charIndex = index,
                            query = query,
                            file = tab.file,
                            projectRoot = projectRoot,
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
                projectRoot = projectRoot,
                query = query,
                openPaths = openPaths,
                emit = ::emit,
            )
        } else {
            searchCodeWithIndex(
                context = context,
                mainViewModel = mainViewModel,
                projectRoot = projectRoot,
                query = query,
                openPaths = openPaths,
                emit = ::emit,
            )
        }
    }

    private suspend fun searchCodeWithIndex(
        context: Context,
        mainViewModel: MainViewModel,
        projectRoot: FileObject,
        query: String,
        openPaths: Set<String>,
        emit: suspend (CodeItem) -> Unit,
    ) {
        var resultLimit = 5
        var offset = 0

        val dao = getDatabase(context, projectRoot).codeIndexDao()

        while (true) {
            val results =
                if (ignoreCase) {
                    dao.search(query, resultLimit, offset)
                } else {
                    dao.searchCaseSensitive(query, resultLimit, offset)
                }
            if (results.isEmpty()) break

            for (result in results) {
                if (result.path in openPaths) continue

                val file = File(result.path).toFileWrapper()
                val fileExt = file.getExtension()
                if (!matchesFileMask(fileExt)) continue

                val indices = findAllIndices(result.content, query, ignoreCase = ignoreCase)
                for (index in indices) {
                    val absoluteCharIndex = result.chunkStart + index

                    currentCoroutineContext().ensureActive()
                    emit(
                        createCodeItem(
                            context = context,
                            mainViewModel = mainViewModel,
                            text = result.content,
                            charIndex = absoluteCharIndex,
                            query = query,
                            file = file,
                            projectRoot = projectRoot,
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
        projectRoot: FileObject,
        query: String,
        openPaths: Set<String>,
        emit: suspend (CodeItem) -> Unit,
        isResultHidden: Boolean = false,
    ) {
        val childFiles = parent.listFiles()

        for (file in childFiles) {
            val path = file.getAbsolutePath()
            if (path in openPaths) continue

            val fileExt = file.getExtension()
            if (file.isFile() && !matchesFileMask(fileExt)) continue

            if (excluder.isExcluded(path)) continue

            val isHidden = file.getName().startsWith(".") || isResultHidden
            if (isHidden && !Settings.show_hidden_files_search) continue

            if (file.isDirectory()) {
                searchCodeWithoutIndex(
                    context = context,
                    mainViewModel = mainViewModel,
                    parent = file,
                    projectRoot = projectRoot,
                    query = query,
                    openPaths = openPaths,
                    emit = emit,
                    isResultHidden = isResultHidden,
                )
                continue
            }

            val fileText = getFileContentOrNull(file) ?: continue

            val lines = fileText.lines()
            lines.forEachIndexed { lineIndex, line ->
                val chunks = line.chunked(MAX_CHUNK_SIZE)
                chunks.forEachIndexed { chunkIndex, chunk ->
                    val indices = findAllIndices(chunk, query, ignoreCase = ignoreCase)
                    for (index in indices) {
                        val absoluteCharIndex = (chunkIndex * MAX_CHUNK_SIZE) + index
                        currentCoroutineContext().ensureActive()
                        emit(
                            createCodeItem(
                                context = context,
                                mainViewModel = mainViewModel,
                                text = chunk,
                                charIndex = absoluteCharIndex,
                                query = query,
                                file = file,
                                projectRoot = projectRoot,
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
                    viewModelScope.launch {
                        mainViewModel.goToTabAndSelect(
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
        val ext = file.getExtension()
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
        indexJob =
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
        cleanupJobs(projectRoot)
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
        currentCoroutineContext().ensureActive()

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
            currentCoroutineContext().ensureActive()
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

        if (excluder.isExcluded(path)) return

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
                currentCoroutineContext().ensureActive()
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
