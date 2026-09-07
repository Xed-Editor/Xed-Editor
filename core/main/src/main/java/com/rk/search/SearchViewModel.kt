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
import com.rk.activities.main.MainViewModel
import com.rk.editor.Editor
import com.rk.file.FileObject
import com.rk.search.code.CodeSearchDirect
import com.rk.search.code.CodeSearchIndexed
import com.rk.search.code.CodeSearchStrategy
import com.rk.search.file.FileSearchDirect
import com.rk.search.file.FileSearchIndexed
import com.rk.search.file.FileSearchStrategy
import com.rk.search.index.FileMeta
import com.rk.search.index.IndexDatabase
import com.rk.search.index.ProjectIndexer
import com.rk.search.utils.GlobExcluder
import com.rk.search.utils.SearchUtils
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.editor.LineEnding
import com.rk.tabs.editor.EditorTab
import com.rk.utils.logDebug
import com.rk.utils.logError
import com.rk.utils.parseExtensions
import com.rk.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

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
    val collapsedFiles = mutableStateListOf<FileObject>()
    private var codeSearchJob: Job? = null

    var codeSearchQuery by mutableStateOf("")
    var codeReplaceQuery by mutableStateOf("")
    var showOptionsMenu by mutableStateOf(false)
    var ignoreCase by mutableStateOf(true)
    var isReplaceShown by mutableStateOf(false)
        private set

    fun toggleCollapsed(file: FileObject) {
        if (collapsedFiles.contains(file)) {
            collapsedFiles.remove(file)
        } else {
            collapsedFiles.add(file)
        }
    }

    fun isCollapsed(file: FileObject): Boolean = collapsedFiles.contains(file)

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
        collapsedFiles.clear()
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

                val openedEditorTabs = mainViewModel.tabs.filterIsInstance<EditorTab>()
                val openPaths = openedEditorTabs.mapNotNull { it.file?.getAbsolutePath() }.toSet()

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
            val file = tab.file ?: continue
            val fileExt = file.getExtension()
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
                                file = file,
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
                onIndexingStateChanged = { isIndexing ->
                    this.isIndexing[projectRoot] = isIndexing
                },
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
