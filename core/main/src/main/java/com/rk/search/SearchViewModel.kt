package com.rk.search

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

class SearchViewModel : ViewModel() {
    private val projectIndexers = mutableMapOf<FileObject, ProjectIndexer>()
    private val _isIndexing = MutableStateFlow<Map<FileObject, Boolean>>(emptyMap())
    val isIndexing = _isIndexing.asStateFlow()

    // File search dialog
    private val _fileSearchQuery = MutableStateFlow("")
    val fileSearchQuery = _fileSearchQuery.asStateFlow()

    private val _isSearchingFiles = MutableStateFlow(false)
    val isSearchingFiles = _isSearchingFiles.asStateFlow()

    private val _fileSearchResults = MutableStateFlow<List<FileMeta>>(emptyList())
    val fileSearchResults = _fileSearchResults.asStateFlow()

    private var fileSearchJob: Job? = null

    // Code search dialog
    private val _showFileMaskDialog = MutableStateFlow(false)
    val showFileMaskDialog = _showFileMaskDialog.asStateFlow()

    private val _fileMaskText = MutableStateFlow(Settings.file_mask)
    val fileMaskText = _fileMaskText.asStateFlow()

    private val fileMask: List<String>
        get() = parseExtensions(_fileMaskText.value)

    private val excluder: GlobExcluder
        get() = GlobExcluder(Settings.excluded_files_search)

    private val _isSearchingCode = MutableStateFlow(false)
    val isSearchingCode = _isSearchingCode.asStateFlow()

    private val _totalCodeSearchResults = MutableStateFlow(0)
    val totalCodeSearchResults = _totalCodeSearchResults.asStateFlow()

    private val _codeSearchResultsOrder = MutableStateFlow<List<FileObject>>(emptyList())
    val codeSearchResultsOrder = _codeSearchResultsOrder.asStateFlow()

    private val _codeSearchResults = MutableStateFlow<Map<FileObject, List<CodeItem>>>(emptyMap())
    val codeSearchResults = _codeSearchResults.asStateFlow()

    private val _collapsedFiles = MutableStateFlow<List<FileObject>>(emptyList())
    val collapsedFiles = _collapsedFiles.asStateFlow()

    private var codeSearchJob: Job? = null

    private val _codeSearchQuery = MutableStateFlow("")
    val codeSearchQuery = _codeSearchQuery.asStateFlow()

    private val _codeReplaceQuery = MutableStateFlow("")
    val codeReplaceQuery = _codeReplaceQuery.asStateFlow()

    private val _showOptionsMenu = MutableStateFlow(false)
    val showOptionsMenu = _showOptionsMenu.asStateFlow()

    private val _ignoreCase = MutableStateFlow(true)
    val ignoreCase = _ignoreCase.asStateFlow()

    private val _isReplaceShown = MutableStateFlow(false)
    val isReplaceShown = _isReplaceShown.asStateFlow()

    private val _isReplacing = MutableStateFlow(false)
    val isReplacing = _isReplacing.asStateFlow()

    fun setFileSearchQuery(value: String) {
        _fileSearchQuery.value = value
    }

    fun setFileMaskText(value: String) {
        _fileMaskText.value = value
    }

    fun setShowFileMaskDialog(value: Boolean) {
        _showFileMaskDialog.value = value
    }

    fun setShowOptionsMenu(value: Boolean) {
        _showOptionsMenu.value = value
    }

    fun setIgnoreCase(value: Boolean) {
        _ignoreCase.value = value
    }

    fun setCodeSearchQuery(value: String) {
        _codeSearchQuery.value = value
    }

    fun setCodeReplaceQuery(value: String) {
        _codeReplaceQuery.value = value
    }

    fun toggleCollapsed(file: FileObject) {
        _collapsedFiles.update { list -> if (list.contains(file)) list - file else list + file }
    }

    fun isCollapsed(file: FileObject): Boolean = _collapsedFiles.value.contains(file)

    companion object {
        // TODO: Occurrence that are between the borders of two chunks won't be found, this is a known issue
        const val MAX_CODE_RESULTS = 10_000 // Cap at 10k entries for code search results
    }

    fun cancelFileSearch() {
        fileSearchJob?.cancel()
        fileSearchJob = null
        _isSearchingFiles.value = false
    }

    fun matchesFileMask(fileExt: String): Boolean {
        if (fileMask.isEmpty()) return true
        return fileMask.any { it == fileExt }
    }

    fun launchFileSearch(context: Context, projectRoot: FileObject) {
        cancelFileSearch()

        _isSearchingFiles.value = true
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

                _fileSearchResults.value = strategy.search(_fileSearchQuery.value, projectRoot)
            } catch (_: CancellationException) {
                logDebug("File search cancelled")
            } catch (e: Exception) {
                logError(e, "Error during file search")
                _fileSearchResults.value = emptyList()
            } finally {
                _isSearchingFiles.value = false
            }
        }
    }

    /** Cancels any running search */
    fun cancelCodeSearch() {
        codeSearchJob?.cancel()
        codeSearchJob = null

        _totalCodeSearchResults.value = 0
        _codeSearchResults.value = emptyMap()
        _codeSearchResultsOrder.value = emptyList()
        _collapsedFiles.value = emptyList()
        _isSearchingCode.value = false
    }

    fun launchCodeSearch(context: Context, mainViewModel: MainViewModel, projectRoot: FileObject) {
        cancelCodeSearch()

        if (_codeSearchQuery.value.isBlank()) {
            _totalCodeSearchResults.value = 0
            _codeSearchResults.value = emptyMap()
            return
        }

        _isSearchingCode.value = true
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
                            ignoreCase = _ignoreCase.value,
                            openPaths = openPaths,
                        )
                    } else {
                        CodeSearchDirect(
                            context = context,
                            projectRoot = projectRoot,
                            mainViewModel = mainViewModel,
                            fileMaskFilter = ::matchesFileMask,
                            excluder = excluder,
                            ignoreCase = _ignoreCase.value,
                            openPaths = openPaths,
                        )
                    }

                strategy.search(_codeSearchQuery.value).collect { codeItem ->
                    if (_totalCodeSearchResults.value < MAX_CODE_RESULTS) {
                        addCodeResult(codeItem)
                        _totalCodeSearchResults.update { it + 1 }
                    } else {
                        _isSearchingCode.value = false
                        codeSearchJob?.cancel()
                    }
                }
            } catch (_: CancellationException) {
                logDebug("Code search cancelled")
            } catch (e: Exception) {
                logError(e, "Error during code search")
            } finally {
                _isSearchingCode.value = false
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
                    val indices = SearchUtils.findAllIndices(line, _codeSearchQuery.value, _ignoreCase.value)
                    for (index in indices) {
                        currentCoroutineContext().ensureActive()

                        val codeItem =
                            SearchUtils.createCodeItem(
                                context = context,
                                mainViewModel = mainViewModel,
                                text = line,
                                charIndex = index,
                                query = _codeSearchQuery.value,
                                file = file,
                                projectRoot = projectRoot,
                                lineIndex = lineIndex,
                                isOpen = true,
                            )

                        addCodeResult(codeItem)
                        _totalCodeSearchResults.update { it + 1 }
                    }
                }
            }
        }
    }

    private fun addCodeResult(codeItem: CodeItem) {
        _codeSearchResults.update { map ->
            if (!map.containsKey(codeItem.file)) {
                _codeSearchResultsOrder.update { it + codeItem.file }
            }
            map + (codeItem.file to ((map[codeItem.file] ?: emptyList()) + codeItem))
        }
    }

    fun toggleReplaceShown() {
        _isReplaceShown.value = !_isReplaceShown.value
    }

    suspend fun replaceIn(mainViewModel: MainViewModel, codeItem: CodeItem) {
        // Pause searches while replacing
        cancelCodeSearch()
        _isReplacing.value = true

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
            _isReplacing.value = false
        }
    }

    suspend fun replaceAllIn(mainViewModel: MainViewModel, codeItems: List<CodeItem>) {
        // Pause searches while replacing
        cancelCodeSearch()
        _isReplacing.value = true

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
            _isReplacing.value = false
        }
    }

    private fun replaceInRawList(codeItem: CodeItem, lines: MutableList<String>) {
        val lineIndex = codeItem.line
        val startCol = codeItem.column
        val diff = codeItem.snippet.highlight.endIndex - codeItem.snippet.highlight.startIndex
        val endCol = codeItem.column + diff

        val line = lines.getOrNull(lineIndex) ?: return
        val newLine = line.replaceRange(startCol, endCol, _codeReplaceQuery.value)
        lines[lineIndex] = newLine
    }

    private suspend fun replaceInEditor(codeItem: CodeItem, editor: Editor) {
        withContext(Dispatchers.Main) {
            val lineIndex = codeItem.line
            val startCol = codeItem.column
            val diff = codeItem.snippet.highlight.endIndex - codeItem.snippet.highlight.startIndex
            val endCol = codeItem.column + diff
            editor.text.replace(lineIndex, startCol, lineIndex, endCol, _codeReplaceQuery.value)
        }
    }

    fun isIndexing(projectRoot: FileObject): Boolean {
        return _isIndexing.value[projectRoot] ?: false
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
                _isIndexing.update { it - projectRoot }
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
            _isIndexing.value = emptyMap()
        }
    }

    private fun getOrCreateIndexer(context: Context, projectRoot: FileObject): ProjectIndexer {
        return projectIndexers.getOrPut(projectRoot) {
            ProjectIndexer(
                context = context,
                projectRoot = projectRoot,
                onIndexingStateChanged = { isIndexing ->
                    _isIndexing.update { it + (projectRoot to isIndexing) }
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
