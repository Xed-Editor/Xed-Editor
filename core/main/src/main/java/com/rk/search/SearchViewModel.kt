package com.rk.search

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rk.activities.main.MainViewModel
import com.rk.editor.Editor
import com.rk.file.FileObject
import com.rk.file.toFileWrapper
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.editor.LineEnding
import com.rk.tabs.editor.EditorTab
import com.rk.utils.hasBinaryChars
import com.rk.utils.isBinaryExtension
import com.rk.utils.logDebug
import com.rk.utils.logError
import com.rk.utils.logWarn
import com.rk.utils.parseExtensions
import com.rk.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

/** Strategy for searching file names. */
private interface FileSearchStrategy {
    suspend fun search(query: String, projectRoot: FileObject): List<FileMeta>
}

/** File search using indexed database. Fast, but requires index to be built. */
private class FileSearchIndexed(private val context: Context) : FileSearchStrategy {
    override suspend fun search(query: String, projectRoot: FileObject): List<FileMeta> =
        withContext(Dispatchers.IO) {
            try {
                IndexDatabase.getDatabase(context, projectRoot).fileMetaDao().search(query)
            } catch (e: Exception) {
                logError(e, "Error searching file index")
                emptyList()
            }
        }
}

/** File search using filesystem traversal. Slower but doesn't require index. */
private class FileSearchDirect(private val excluder: GlobExcluder) : FileSearchStrategy {
    override suspend fun search(query: String, projectRoot: FileObject): List<FileMeta> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileMeta>()

            suspend fun searchRecursively(parent: FileObject) {
                try {
                    val childFiles = parent.listFiles()

                    for (file in childFiles) {
                        currentCoroutineContext().ensureActive()

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
                            searchRecursively(file)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during file search")
                }
            }

            searchRecursively(projectRoot)
            results
        }
}

/** Strategy for searching code content. */
private interface CodeSearchStrategy {
    fun search(query: String): Flow<CodeItem>
}

/** Code search using indexed database. Returns results as Flow for streaming. */
private class CodeSearchIndexed(
    private val context: Context,
    private val projectRoot: FileObject,
    private val mainViewModel: MainViewModel,
    private val fileMaskFilter: (String) -> Boolean,
    private val ignoreCase: Boolean,
    private val openPaths: Set<String>,
) : CodeSearchStrategy {
    override fun search(query: String): Flow<CodeItem> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                val dao = IndexDatabase.getDatabase(context, projectRoot).codeIndexDao()
                var resultLimit = 5
                var offset = 0

                while (true) {
                    currentCoroutineContext().ensureActive()

                    val results =
                        if (ignoreCase) {
                            dao.search(query, resultLimit, offset)
                        } else {
                            dao.searchCaseSensitive(query, resultLimit, offset)
                        }
                    if (results.isEmpty()) break

                    for (result in results) {
                        try {
                            if (result.path in openPaths) continue
                            val file = File(result.path).toFileWrapper()
                            val fileExt = file.getExtension()

                            if (!fileMaskFilter(fileExt)) continue

                            val indices = SearchUtils.findAllIndices(result.content, query, ignoreCase = ignoreCase)
                            for (index in indices) {
                                val absoluteCharIndex = result.chunkStart + index

                                currentCoroutineContext().ensureActive()
                                send(
                                    SearchUtils.createCodeItem(
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
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logError(e, "Error processing indexed code result")
                        }
                    }
                    offset += resultLimit
                    resultLimit = 20
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e, "Error searching code index")
            }
        }
    }
}

/** Code search using filesystem traversal. No index required. */
private class CodeSearchDirect(
    private val context: Context,
    private val projectRoot: FileObject,
    private val mainViewModel: MainViewModel,
    private val fileMaskFilter: (String) -> Boolean,
    private val excluder: GlobExcluder,
    private val ignoreCase: Boolean,
    private val openPaths: Set<String>,
) : CodeSearchStrategy {

    companion object {
        private const val MAX_CHUNK_SIZE = 1_000_000 // 1 MB limit per column
    }

    override fun search(query: String): Flow<CodeItem> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                searchRecursively(parent = projectRoot, isResultHidden = false, query = query, sendFn = { send(it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e, "Error during direct code search")
            }
        }
    }

    private suspend fun searchRecursively(
        parent: FileObject,
        isResultHidden: Boolean,
        query: String,
        sendFn: suspend (CodeItem) -> Unit,
    ) {
        try {
            val childFiles = parent.listFiles()

            for (file in childFiles) {
                currentCoroutineContext().ensureActive()

                val path = file.getAbsolutePath()
                if (path in openPaths) continue

                val fileExt = file.getExtension()
                if (file.isFile() && !fileMaskFilter(fileExt)) continue

                if (excluder.isExcluded(path)) continue

                val isHidden = file.getName().startsWith(".") || isResultHidden
                if (isHidden && !Settings.show_hidden_files_search) continue

                if (file.isDirectory()) {
                    searchRecursively(file, isHidden, query, sendFn)
                    continue
                }

                if (!SearchUtils.isFileSearchable(file)) continue
                val charset = Charset.forName(Settings.encoding)

                file.useInputStream { inputStream ->
                    inputStream.bufferedReader(charset).useLines { lineSequence ->
                        lineSequence.forEachIndexed { lineIndex, line ->
                            val chunks = line.chunked(MAX_CHUNK_SIZE)
                            chunks.forEachIndexed { chunkIndex, chunk ->
                                val indices = SearchUtils.findAllIndices(chunk, query, ignoreCase = ignoreCase)
                                for (index in indices) {
                                    currentCoroutineContext().ensureActive()
                                    val absoluteCharIndex = (chunkIndex * MAX_CHUNK_SIZE) + index
                                    currentCoroutineContext().ensureActive()
                                    sendFn(
                                        SearchUtils.createCodeItem(
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
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error during recursive file search")
        }
    }
}

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

/** Manages indexing for a single project with proper lifecycle management. */
private class ProjectIndexer(
    private val context: Context,
    private val projectRoot: FileObject,
    private val excluder: GlobExcluder,
    private val onIndexingStateChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
) {
    companion object {
        private const val CODE_BATCH_SIZE = 5_000
        private const val MAX_CHUNK_SIZE = 1_000_000
        private const val MAX_FILE_SIZE_SEARCH = 10_000_000
    }

    private var indexingJob: Job? = null

    /**
     * Starts full indexing of the project. Cancels any previous indexing job first. Index stores ALL files and code (no
     * filtering by file_mask or excluder).
     */
    suspend fun startIndexing() {
        indexingJob?.cancelAndJoin()

        onIndexingStateChanged(true)

        indexingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val database =
                        try {
                            IndexDatabase.getDatabase(context, projectRoot)
                        } catch (e: Exception) {
                            logError(e, "Failed to get index database for sync, attempting recovery")
                            attemptDatabaseRecovery()
                            IndexDatabase.getDatabase(context, projectRoot)
                        }

                    val codeLineDao = database.codeIndexDao()
                    val fileMetaDao = database.fileMetaDao()

                    val indexedFiles = fileMetaDao.getAll().associateBy { it.path }
                    val pathsToKeep = mutableSetOf<String>()
                    val newCodeLines = mutableListOf<CodeLine>()
                    val newFileMetas = mutableListOf<FileMeta>()

                    indexRecursively(projectRoot, indexedFiles, pathsToKeep, newCodeLines, newFileMetas, codeLineDao)

                    finalizeIndex(
                        database,
                        indexedFiles,
                        pathsToKeep,
                        codeLineDao,
                        fileMetaDao,
                        newCodeLines,
                        newFileMetas,
                    )

                    logDebug("Indexing completed for $projectRoot")
                } catch (e: CancellationException) {
                    logDebug("Indexing cancelled for $projectRoot")
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during indexing")
                    onError("Indexing failed: ${e.message}")
                } finally {
                    onIndexingStateChanged(false)
                }
            }
    }

    /** Incremental sync of a specific file or directory. Only re-indexes changed files under the given path. */
    suspend fun syncFile(file: FileObject) {
        indexingJob?.cancelAndJoin()

        onIndexingStateChanged(true)

        indexingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val database =
                        try {
                            IndexDatabase.getDatabase(context, projectRoot)
                        } catch (e: Exception) {
                            logError(e, "Failed to get index database for sync, attempting recovery")
                            attemptDatabaseRecovery()
                            IndexDatabase.getDatabase(context, projectRoot)
                        }

                    val codeLineDao = database.codeIndexDao()
                    val fileMetaDao = database.fileMetaDao()

                    val allIndexedFiles = fileMetaDao.getAll().associateBy { it.path }

                    // Only consider files under the changed path
                    val relevantIndexedFiles =
                        if (file == projectRoot) {
                            allIndexedFiles
                        } else {
                            allIndexedFiles.filter { it.key.startsWith(file.getAbsolutePath()) }
                        }

                    val pathsToKeep = mutableSetOf<String>()
                    val newCodeLines = mutableListOf<CodeLine>()
                    val newFileMetas = mutableListOf<FileMeta>()

                    if (file.isDirectory()) {
                        indexRecursively(
                            parent = file,
                            indexedFiles = relevantIndexedFiles,
                            pathsToKeep = pathsToKeep,
                            codeLineResults = newCodeLines,
                            fileMetaResults = newFileMetas,
                            codeLineDao = codeLineDao,
                        )
                    } else {
                        indexFile(
                            file = file,
                            indexedFiles = relevantIndexedFiles,
                            pathsToKeep = pathsToKeep,
                            codeLineResults = newCodeLines,
                            fileMetaResults = newFileMetas,
                            codeLineDao = codeLineDao,
                        )
                    }

                    finalizeIndex(
                        database = database,
                        indexedFiles = relevantIndexedFiles,
                        pathsToKeep = pathsToKeep,
                        codeLineDao = codeLineDao,
                        fileMetaDao = fileMetaDao,
                        newCodeLines = newCodeLines,
                        newFileMetas = newFileMetas,
                    )

                    logDebug("Sync completed for $file")
                } catch (e: CancellationException) {
                    logDebug("Sync cancelled for $file")
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during file sync")
                    onError("Sync failed: ${e.message}")
                } finally {
                    onIndexingStateChanged(false)
                }
            }

        indexingJob?.join()
    }

    /** Cancels any ongoing indexing operation and waits for it to complete. */
    suspend fun cancelIndexing() {
        indexingJob?.cancelAndJoin()
        indexingJob = null
        onIndexingStateChanged(false)
    }

    /** Closes the database and cleans up resources. Does NOT delete the database file. */
    fun closeDatabase() {
        try {
            IndexDatabase.closeInstance(projectRoot)
            logDebug("Closed index database for $projectRoot")
        } catch (e: Exception) {
            logError(e, "Error closing database")
        }
    }

    private suspend fun attemptDatabaseRecovery() {
        return withContext(Dispatchers.IO) {
            try {
                logWarn("Attempting database recovery by deleting corrupt database")
                IndexDatabase.removeDatabase(context, projectRoot)
                onError("Index was corrupted and has been rebuilt. Please try your search again.")
            } catch (e: Exception) {
                logError(e, "Failed to recover database")
            }
        }
    }

    /** Gets current indexing statistics. */
    suspend fun getStats(): SearchViewModel.IndexingStats {
        return withContext(Dispatchers.IO) {
            try {
                val database = IndexDatabase.getDatabase(context, projectRoot)
                val totalFiles = database.fileMetaDao().getCount()
                val databaseSize = IndexDatabase.getDatabaseSize(context, projectRoot)
                SearchViewModel.IndexingStats(totalFiles, databaseSize)
            } catch (e: Exception) {
                logError(e, "Error getting indexing stats")
                SearchViewModel.IndexingStats(0, 0)
            }
        }
    }

    private suspend fun indexRecursively(
        parent: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        codeLineDao: CodeLineDao,
        isResultHidden: Boolean = false,
    ) {
        try {
            val childFiles = parent.listFiles()

            for (file in childFiles) {
                currentCoroutineContext().ensureActive()
                indexFile(
                    file = file,
                    indexedFiles = indexedFiles,
                    pathsToKeep = pathsToKeep,
                    codeLineResults = codeLineResults,
                    fileMetaResults = fileMetaResults,
                    codeLineDao = codeLineDao,
                    isResultHidden = isResultHidden,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error during recursive indexing")
        }
    }

    private suspend fun indexFile(
        file: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        codeLineDao: CodeLineDao,
        isResultHidden: Boolean = false,
    ) {
        try {
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
                    codeLineDao = codeLineDao,
                    isResultHidden = isHidden,
                )
                return
            }

            if (!SearchUtils.isFileSearchable(file)) return

            val charset = Charset.forName(Settings.encoding)
            file.useInputStream { inputStream ->
                inputStream.bufferedReader(charset).useLines { lineSequence ->
                    lineSequence.forEachIndexed { lineIndex, line ->
                        currentCoroutineContext().ensureActive()

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

                            // Flush batch to avoid OOM
                            if (codeLineResults.size > CODE_BATCH_SIZE) {
                                codeLineDao.insertAll(codeLineResults)
                                codeLineResults.clear()
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error indexing file: ${file.getAbsolutePath()}")
        }
    }

    private suspend fun finalizeIndex(
        database: IndexDatabase,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineDao: CodeLineDao,
        fileMetaDao: FileMetaDao,
        newCodeLines: MutableList<CodeLine>,
        newFileMetas: MutableList<FileMeta>,
    ) {
        return withContext(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()

                database.withTransaction {
                    // Delete files that are no longer present or were modified
                    val deletedPaths = indexedFiles.keys - pathsToKeep
                    for (path in deletedPaths) {
                        codeLineDao.deleteByPath(path)
                        fileMetaDao.deleteByPath(path)
                    }

                    // Insert new/updated entries
                    if (newCodeLines.isNotEmpty()) {
                        codeLineDao.insertAll(newCodeLines)
                    }
                    if (newFileMetas.isNotEmpty()) {
                        fileMetaDao.insertAll(newFileMetas)
                    }
                }
            } catch (e: Exception) {
                logError(e, "Error finalizing index")
                throw e
            }
        }
    }
}

class SearchViewModel : ViewModel() {
    private val projectIndexers = mutableMapOf<FileObject, ProjectIndexer>()
    private var isIndexing = mutableStateMapOf<FileObject, Boolean>()

    // File search dialog
    var fileSearchQuery by mutableStateOf("")
    var isSearchingFiles by mutableStateOf(false)
    var fileSearchResults by mutableStateOf<List<FileMeta>>(emptyList())
    private var fileSearchJob: Job? = null

    // Code search dialog
    var showFileMaskDialog by mutableStateOf(false)
    var fileMaskText by mutableStateOf(Settings.file_mask)
    var fileMask = derivedStateOf { parseExtensions(fileMaskText) }
    private val excluder by derivedStateOf { GlobExcluder(Settings.excluded_files_search) }

    var isSearchingCode by mutableStateOf(false)
    var totalCodeSearchResults by mutableIntStateOf(0)
    val codeSearchResultsOrder = mutableStateListOf<FileObject>()
    val codeSearchResults = mutableStateMapOf<FileObject, SnapshotStateList<CodeItem>>()
    private var codeSearchJob: Job? = null

    var codeSearchQuery by mutableStateOf("")
    var codeReplaceQuery by mutableStateOf("")
    var showOptionsMenu by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var isReplaceShown by mutableStateOf(false)
        private set

    companion object {
        // TODO: Occurrence that are between the borders of two chunks won't be found, this is a known issue
        const val MAX_CODE_RESULTS = 10_000 // Cap at 10k entries for code search results
    }

    var isReplacing by mutableStateOf(false)

    fun cancelFileSearch() {
        fileSearchJob?.cancel()
        fileSearchJob = null
        isSearchingFiles = false
    }

    fun matchesFileMask(fileExt: String): Boolean {
        if (fileMask.value.isEmpty()) return true
        return fileMask.value.any { it == fileExt }
    }

    fun launchFileSearch(context: Context, projectRoot: FileObject) {
        cancelFileSearch()

        isSearchingFiles = true
        fileSearchJob = viewModelScope.launch {
            try {
                val useIndex =
                    Preference.getBoolean(
                        "enable_indexing_${projectRoot.hashCode()}",
                        Settings.always_index_projects,
                    )

                val strategy: FileSearchStrategy =
                    if (useIndex) {
                        FileSearchIndexed(context)
                    } else {
                        FileSearchDirect(excluder)
                    }

                fileSearchResults = strategy.search(fileSearchQuery, projectRoot)
            } catch (_: CancellationException) {
                logDebug("File search cancelled")
            } catch (e: Exception) {
                logError(e, "Error during file search")
                fileSearchResults = emptyList()
            } finally {
                isSearchingFiles = false
            }
        }
    }

    /** Cancels any running search */
    fun cancelCodeSearch() {
        codeSearchJob?.cancel()
        codeSearchJob = null

        totalCodeSearchResults = 0
        codeSearchResults.clear()
        codeSearchResultsOrder.clear()
        isSearchingCode = false
    }

    fun launchCodeSearch(context: Context, mainViewModel: MainViewModel, projectRoot: FileObject) {
        cancelCodeSearch()

        if (codeSearchQuery.isBlank()) {
            totalCodeSearchResults = 0
            codeSearchResults.clear()
            return
        }

        isSearchingCode = true
        codeSearchJob = viewModelScope.launch {
            try {
                val useIndex =
                    Preference.getBoolean(
                        "enable_indexing_${projectRoot.hashCode()}",
                        Settings.always_index_projects,
                    )

                val openedEditorTabs = mainViewModel.tabs.mapNotNull { it as? EditorTab }
                val openPaths = openedEditorTabs.map { it.file.getAbsolutePath() }.toSet()

                // Emit results from open editor tabs first
                scanOpenTabs(openedEditorTabs, context, mainViewModel, projectRoot)

                // Search in remaining files
                val strategy: CodeSearchStrategy =
                    if (useIndex) {
                        CodeSearchIndexed(
                            context = context,
                            projectRoot = projectRoot,
                            mainViewModel = mainViewModel,
                            fileMaskFilter = ::matchesFileMask,
                            ignoreCase = ignoreCase,
                            openPaths = openPaths,
                        )
                    } else {
                        CodeSearchDirect(
                            context = context,
                            projectRoot = projectRoot,
                            mainViewModel = mainViewModel,
                            fileMaskFilter = ::matchesFileMask,
                            excluder = excluder,
                            ignoreCase = ignoreCase,
                            openPaths = openPaths,
                        )
                    }

                strategy.search(codeSearchQuery).collect { codeItem ->
                    if (totalCodeSearchResults < MAX_CODE_RESULTS) {
                        addCodeResult(codeItem)
                        totalCodeSearchResults++
                    } else {
                        isSearchingCode = false
                        codeSearchJob?.cancel()
                    }
                }
            } catch (_: CancellationException) {
                logDebug("Code search cancelled")
            } catch (e: Exception) {
                logError(e, "Error during code search")
            } finally {
                isSearchingCode = false
            }
        }
    }

    private suspend fun scanOpenTabs(
        openedEditorTabs: List<EditorTab>,
        context: Context,
        mainViewModel: MainViewModel,
        projectRoot: FileObject,
    ) {
        for (tab in openedEditorTabs) {
            val fileExt = tab.file.getExtension()
            if (!matchesFileMask(fileExt)) continue

            val editor = tab.editorState.editor.get()
            val content = editor?.text
            if (content != null) {
                val lineCount = content.lineCount
                for (lineIndex in 0 until lineCount) {

                    val line = content.getLine(lineIndex).toString()
                    val indices = SearchUtils.findAllIndices(line, codeSearchQuery, ignoreCase)
                    for (index in indices) {
                        currentCoroutineContext().ensureActive()

                        val codeItem =
                            SearchUtils.createCodeItem(
                                context = context,
                                mainViewModel = mainViewModel,
                                text = line,
                                charIndex = index,
                                query = codeSearchQuery,
                                file = tab.file,
                                projectRoot = projectRoot,
                                lineIndex = lineIndex,
                                isOpen = true,
                            )

                        addCodeResult(codeItem)
                        totalCodeSearchResults++
                    }
                }
            }
        }
    }

    private fun addCodeResult(codeItem: CodeItem) {
        if (!codeSearchResults.containsKey(codeItem.file)) {
            codeSearchResultsOrder.add(codeItem.file)
        }
        val fileList = codeSearchResults.getOrPut(codeItem.file) { mutableStateListOf() }
        fileList.add(codeItem)
    }

    fun toggleReplaceShown() {
        isReplaceShown = !isReplaceShown
    }

    suspend fun replaceIn(mainViewModel: MainViewModel, codeItem: CodeItem) {
        // Pause searches while replacing
        cancelCodeSearch()
        isReplacing = true

        try {
            withContext(Dispatchers.IO) {
                if (codeItem.isOpen) {
                    val tab =
                        mainViewModel.tabs.filterIsInstance<EditorTab>().find { tab -> tab.file == codeItem.file }
                            ?: return@withContext
                    val editor = tab.editorState.editor.get() ?: return@withContext
                    replaceInEditor(codeItem, editor)
                } else {
                    val content = codeItem.file.readText() ?: return@withContext
                    val lines = content.lines().toMutableList()

                    replaceInRawList(codeItem, lines)

                    val charset = Charset.forName(Settings.encoding)
                    val lineEnding = LineEnding.detect(content)
                    val normalizedContent = lines.joinToString(lineEnding.char)
                    codeItem.file.writeText(normalizedContent, charset)
                }
            }

            syncIndex(codeItem.file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error replacing text")
        } finally {
            isReplacing = false
        }
    }

    suspend fun replaceAllIn(mainViewModel: MainViewModel, codeItems: List<CodeItem>) {
        // Pause searches while replacing
        cancelCodeSearch()
        isReplacing = true

        try {
            val groupedItems = codeItems.groupBy { it.file }

            withContext(Dispatchers.IO) {
                for ((file, items) in groupedItems) {
                    val itemsSorted =
                        items.sortedWith(compareByDescending<CodeItem> { it.line }.thenByDescending { it.column })
                    val firstItem = itemsSorted.first()
                    if (firstItem.isOpen) {
                        val tab = mainViewModel.tabs.filterIsInstance<EditorTab>().find { tab -> tab.file == file }
                        val editor = tab?.editorState?.editor?.get()
                        if (editor != null) {
                            for (codeItem in itemsSorted) {
                                replaceInEditor(codeItem, editor)
                            }
                        }
                    } else {
                        val content = file.readText() ?: continue
                        val lines = content.lines().toMutableList()

                        for (codeItem in itemsSorted) {
                            replaceInRawList(codeItem, lines)
                        }

                        val charset = Charset.forName(Settings.encoding)
                        val lineEnding = LineEnding.detect(content)
                        val normalizedContent = lines.joinToString(lineEnding.char)
                        file.writeText(normalizedContent, charset)
                    }

                    syncIndex(file)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error replacing all text")
        } finally {
            isReplacing = false
        }
    }

    private fun replaceInRawList(codeItem: CodeItem, lines: MutableList<String>) {
        val lineIndex = codeItem.line
        val startCol = codeItem.column
        val diff = codeItem.snippet.highlight.endIndex - codeItem.snippet.highlight.startIndex
        val endCol = codeItem.column + diff

        val line = lines.getOrNull(lineIndex) ?: return
        val newLine = line.replaceRange(startCol, endCol, codeReplaceQuery)
        lines[lineIndex] = newLine
    }

    private suspend fun replaceInEditor(codeItem: CodeItem, editor: Editor) {
        withContext(Dispatchers.Main) {
            val lineIndex = codeItem.line
            val startCol = codeItem.column
            val diff = codeItem.snippet.highlight.endIndex - codeItem.snippet.highlight.startIndex
            val endCol = codeItem.column + diff
            editor.text.replace(lineIndex, startCol, lineIndex, endCol, codeReplaceQuery)
        }
    }

    fun isIndexing(projectRoot: FileObject): Boolean {
        return isIndexing[projectRoot] ?: false
    }

    suspend fun index(context: Context, projectRoot: FileObject) {
        val indexer = getOrCreateIndexer(context, projectRoot)
        indexer.startIndexing()
    }

    fun deleteIndex(context: Context, projectRoot: FileObject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val indexer = projectIndexers[projectRoot]
                if (indexer != null) {
                    indexer.cancelIndexing()
                    projectIndexers.remove(projectRoot)
                }
                IndexDatabase.removeDatabase(context, projectRoot)
                isIndexing.remove(projectRoot)
            } catch (e: Exception) {
                logError(e, "Error deleting index")
            }
        }
    }

    fun syncIndex(file: FileObject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val databases = IndexDatabase.findDatabasesFor(file)
                for (database in databases) {
                    val indexer = projectIndexers[database.projectRoot]
                    indexer?.syncFile(file)
                }
            } catch (e: Exception) {
                logError(e, "Error syncing index")
            }
        }
    }

    data class IndexingStats(val totalFiles: Int, val databaseSize: Long)

    suspend fun getStats(context: Context, projectRoot: FileObject): IndexingStats {
        return withContext(Dispatchers.IO) {
            try {
                val indexer = getOrCreateIndexer(context, projectRoot)
                indexer.getStats()
            } catch (e: Exception) {
                logError(e, "Error getting stats")
                IndexingStats(0, 0)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        fileSearchJob?.cancel()
        fileSearchJob = null
        codeSearchJob?.cancel()
        codeSearchJob = null

        viewModelScope.launch {
            for ((_, indexer) in projectIndexers) {
                try {
                    indexer.cancelIndexing()
                    indexer.closeDatabase()
                } catch (e: Exception) {
                    logError(e, "Error cleaning up indexer")
                }
            }
            projectIndexers.clear()
            isIndexing.clear()
        }
    }

    private fun getOrCreateIndexer(context: Context, projectRoot: FileObject): ProjectIndexer {
        return projectIndexers.getOrPut(projectRoot) {
            ProjectIndexer(
                context = context,
                projectRoot = projectRoot,
                onIndexingStateChanged = { isIndexing -> this.isIndexing[projectRoot] = isIndexing },
                onError = { errorMessage ->
                    logError("Indexer error: $errorMessage")
                    toast("Indexer error: $errorMessage")
                },
                viewModelScope = viewModelScope,
                excluder = excluder,
            )
        }
    }
}
