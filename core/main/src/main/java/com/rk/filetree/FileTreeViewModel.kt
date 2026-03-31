package com.rk.filetree

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.gitViewModel
import com.rk.activities.main.searchViewModel
import com.rk.file.FileObject
import com.rk.search.GlobExcluder
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(file = this, isFile = isFile(), isDirectory = isDirectory(), name = getAppropriateName())
}

class FileTreeViewModel : ViewModel() {
    // File option dialogs
    var showRenameDialog by mutableStateOf(false)
        private set

    var renameFile by mutableStateOf<FileObject?>(null)
        private set

    var renameValue by mutableStateOf("")

    var renameError by mutableStateOf<String?>(null)
    var showDeleteConfirmation by mutableStateOf(false)
        private set

    var deleteFiles by mutableStateOf<List<FileObject>?>(null)
        private set

    var deleteRoot by mutableStateOf<FileObject?>(null)
        private set

    var showPropertiesDialog by mutableStateOf(false)
        private set

    var propertyFile by mutableStateOf<FileObject?>(null)
        private set

    var isCreateFile by mutableStateOf(true)
        private set

    var createValue by mutableStateOf("")
    var createError by mutableStateOf<String?>(null)
    var showCreateDialog by mutableStateOf(false)
        private set

    var createParentFile by mutableStateOf<FileObject?>(null)
        private set

    var createRoot by mutableStateOf<FileObject?>(null)
        private set

    var showCloseProjectConfirmation by mutableStateOf(false)
        private set

    var projectConfirmationRoot by mutableStateOf<FileObject?>(null)
        private set

    fun showRenameDialog(file: FileObject) {
        showRenameDialog = true
        renameValue = file.getName()
        renameFile = file
    }

    fun closeRenameDialog() {
        showRenameDialog = false
        renameValue = ""
        renameError = null
        renameFile = null
    }

    fun showDeleteConfirmation(files: List<FileObject>, root: FileObject?) {
        showDeleteConfirmation = true
        deleteFiles = files
        deleteRoot = root
    }

    fun closeDeleteConfirmation() {
        showDeleteConfirmation = false
        deleteFiles = null
        deleteRoot = null
    }

    fun showPropertiesDialog(file: FileObject) {
        showPropertiesDialog = true
        propertyFile = file
    }

    fun closePropertiesDialog() {
        showPropertiesDialog = false
        propertyFile = null
    }

    fun showCreateDialog(isCreateFile: Boolean, parentFile: FileObject, root: FileObject?) {
        this.isCreateFile = isCreateFile
        showCreateDialog = true
        createParentFile = parentFile
        createRoot = root
    }

    fun closeCreateDialog() {
        showCreateDialog = false
        createError = null
        createParentFile = null
        createRoot = null
    }

    fun showCloseProjectConfirmation(root: FileObject) {
        showCloseProjectConfirmation = true
        projectConfirmationRoot = root
    }

    fun closeCloseProjectConfirmation() {
        showCloseProjectConfirmation = false
        projectConfirmationRoot = null
    }

    // File tree
    var sortMode by mutableStateOf(SortMode.entries[Settings.sort_mode])
    private val selectedFiles = mutableStateMapOf<FileObject, List<FileObject>>()
    private val focusedFile = mutableStateMapOf<FileObject, FileObject>()
    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val expandedNodes = mutableStateMapOf<FileObject, Boolean>()
    private val collapsedNameCache = mutableStateMapOf<FileObject, String>()
    private var fileOperationsCount by mutableIntStateOf(0)

    private val excluder by derivedStateOf { GlobExcluder(Settings.excluded_files_drawer) }

    fun getExpandedNodes(): Map<FileObject, Boolean> {
        return mutableMapOf<FileObject, Boolean>().apply { expandedNodes.forEach { set(it.key, it.value) } }
    }

    fun setExpandedNodes(map: Map<FileObject, Boolean>) {
        map.forEach { expandedNodes[it.key] = it.value }
    }

    fun toggleSelection(projectRoot: FileObject, fileObject: FileObject) {
        if (isFileSelected(projectRoot, fileObject)) {
            unselectFile(projectRoot, fileObject)
        } else {
            selectFile(projectRoot, fileObject)
        }
    }

    fun selectFile(projectRoot: FileObject, fileObject: FileObject) {
        selectedFiles[projectRoot] = selectedFiles[projectRoot]?.plus(fileObject) ?: listOf(fileObject)
    }

    fun unselectFile(projectRoot: FileObject, fileObject: FileObject) {
        selectedFiles[projectRoot] = selectedFiles[projectRoot]?.minus(fileObject) ?: listOf(fileObject)
        if (selectedFiles[projectRoot]?.isEmpty() == true) {
            selectedFiles.remove(projectRoot)
        }
    }

    fun unselectAllFiles(projectRoot: FileObject) {
        selectedFiles.remove(projectRoot)
    }

    fun isFileSelected(projectRoot: FileObject, fileObject: FileObject): Boolean {
        return selectedFiles[projectRoot]?.contains(fileObject) == true
    }

    fun isAnyFileSelected(projectRoot: FileObject): Boolean {
        return selectedFiles[projectRoot]?.isNotEmpty() == true
    }

    fun getSelectionCount(projectRoot: FileObject): Int {
        return selectedFiles[projectRoot]?.size ?: 0
    }

    fun getSelectedFiles(projectRoot: FileObject): List<FileObject> {
        return selectedFiles[projectRoot] ?: emptyList()
    }

    suspend fun withFileOperation(block: suspend () -> Unit) {
        registerFileOperation()
        try {
            block()
        } finally {
            unregisterFileOperation()
        }
    }

    fun registerFileOperation() {
        fileOperationsCount++
    }

    fun unregisterFileOperation() {
        fileOperationsCount--
    }

    fun isFileOperationInProgress(): Boolean {
        return fileOperationsCount > 0
    }

    private val cutNodes = mutableStateListOf<FileObject>()

    // File -> Error severity (see DiagnosticRegion.java)
    private val diagnosedNodes = mutableStateMapOf<FileObject, Int>()

    // Track loading states to avoid showing spinners incorrectly
    private val _loadingStates = mutableStateMapOf<FileObject, Boolean>()

    fun isNodeExpanded(fileObject: FileObject): Boolean = expandedNodes[fileObject] == true

    fun isNodeLoading(fileObject: FileObject): Boolean = _loadingStates[fileObject] == true

    fun isNodeCut(fileObject: FileObject): Boolean = cutNodes.contains(fileObject)

    fun markNodeAsCut(fileObject: FileObject) {
        cutNodes.add(fileObject)
    }

    fun unmarkNodeAsCut(fileObject: FileObject) {
        cutNodes.remove(fileObject)
    }

    fun diagnoseNode(fileObject: FileObject, severity: Int) {
        diagnosedNodes[fileObject] = severity
    }

    fun undiagnoseNode(fileObject: FileObject) {
        diagnosedNodes.remove(fileObject)
    }

    fun getNodeSeverity(fileObject: FileObject): Int {
        return diagnosedNodes[fileObject] ?: -1
    }

    fun toggleNodeExpansion(fileObject: FileObject) {
        val wasExpanded = expandedNodes[fileObject] == true
        expandedNodes[fileObject] = !wasExpanded

        // If we're expanding and haven't loaded yet, trigger a load
        if (!wasExpanded && !fileListCache.containsKey(fileObject)) {
            _loadingStates[fileObject] = true
        }
    }

    fun getCollapsedName(node: FileTreeNode): String {
        return collapsedNameCache[node.file] ?: node.name
    }

    suspend fun collapseNode(node: FileTreeNode): FileTreeNode {
        var currentNode = node
        var collapsedName = node.name
        while (true) {
            if (!isNodeExpanded(currentNode.file)) {
                toggleNodeExpansion(currentNode.file)
            }
            loadChildrenForNodeSynchronous(currentNode)
            val children = getNodeChildren(currentNode)
            if (children.size != 1) {
                break
            }
            val child = children.first()
            if (!child.isDirectory) {
                break
            }
            collapsedName += "/${child.name}"
            currentNode = child
        }
        collapsedNameCache[node.file] = collapsedName
        return currentNode
    }

    fun updateCache(file: FileObject) {
        searchViewModel.get()?.syncIndex(file)
        gitViewModel.get()?.syncChanges(file.getAbsolutePath())
        if (file.isDirectory().not()) {
            return
        }
        collapsedNameCache.remove(file)
        _loadingStates[file] = true // Mark as loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Safely access file listing
                val fileList =
                    try {
                        file.listFiles()
                    } catch (e: Exception) {
                        _loadingStates[file] = false
                        return@launch
                    }

                // Process files
                val sortedFiles = sortAndFilterFiles(fileList)

                fileListCache[file] = sortedFiles

                viewModelScope.launch {
                    delay(300)
                    _loadingStates[file] = false
                }
            } catch (e: Exception) {
                _loadingStates[file] = false
            }
        }
    }

    fun isFileFocused(projectFile: FileObject, fileObject: FileObject) = focusedFile[projectFile] == fileObject

    suspend fun goToFolder(projectFile: FileObject, fileObject: FileObject) {
        focusedFile[projectFile] = fileObject
        viewModelScope.launch {
            delay(1000)
            focusedFile.remove(projectFile)
        }

        var currentFile: FileObject? = fileObject
        while (currentFile != null && currentFile != projectFile) {
            expandedNodes[currentFile] = true

            // If we're expanding and haven't loaded yet, trigger a load
            if (!fileListCache.containsKey(fileObject)) {
                _loadingStates[currentFile] = true
            }

            currentFile = currentFile.getParentFile()
        }
        expandedNodes[projectFile] = true
    }

    suspend fun refreshEverything() =
        withContext(Dispatchers.IO) { fileListCache.keys.toList().forEach { updateCache(it) } }

    fun getNodeChildren(node: FileTreeNode): List<FileTreeNode> {
        return fileListCache[node.file] ?: emptyList()
    }

    fun loadChildrenForNode(node: FileTreeNode) {
        // If already in cache, don't reload
        if (fileListCache.containsKey(node.file)) {
            _loadingStates[node.file] = false
            return
        }

        // Set loading state
        _loadingStates[node.file] = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Safely access file listing
                val fileList =
                    try {
                        node.file.listFiles()
                    } catch (_: Exception) {
                        _loadingStates[node.file] = false
                        return@launch
                    }

                // Process files
                val sortedFiles = sortAndFilterFiles(fileList)

                fileListCache[node.file] = sortedFiles
                viewModelScope.launch {
                    delay(300)
                    _loadingStates[node.file] = false
                }
            } catch (_: Exception) {
                _loadingStates[node.file] = false
            }
        }
    }

    suspend fun loadChildrenForNodeSynchronous(node: FileTreeNode) {
        // If already in cache, don't reload
        if (fileListCache.containsKey(node.file)) {
            _loadingStates[node.file] = false
            return
        }

        // Set loading state
        _loadingStates[node.file] = true

        try {
            // Safely access file listing
            val fileList =
                try {
                    node.file.listFiles()
                } catch (_: Exception) {
                    _loadingStates[node.file] = false
                    return
                }

            // Process files
            val sortedFiles = sortAndFilterFiles(fileList)

            fileListCache[node.file] = sortedFiles
            viewModelScope.launch {
                delay(300)
                _loadingStates[node.file] = false
            }
        } catch (_: Exception) {
            _loadingStates[node.file] = false
        }
    }

    private suspend fun calculateFileSizes(fileObjects: List<FileObject>): Map<FileObject, Long> {
        val fileSizes = mutableMapOf<FileObject, Long>()
        if (sortMode != SortMode.SORT_BY_SIZE) return fileSizes

        fileObjects.forEach { file ->
            if (!file.isDirectory()) {
                fileSizes[file] = file.length()
            }
        }
        return fileSizes
    }

    private suspend fun sortAndFilterFiles(fileObjects: List<FileObject>): List<FileTreeNode> {
        val fileSizes = calculateFileSizes(fileObjects)

        return fileObjects
            .sortedWith(
                compareBy<FileObject> { !it.isDirectory() }
                    .thenComparator { f1, f2 ->
                        when (sortMode) {
                            SortMode.SORT_BY_NAME ->
                                f1.getName().lowercase().compareTo(f2.getName().lowercase()) // A -> Z
                            SortMode.SORT_BY_SIZE ->
                                (fileSizes[f2] ?: 0L).compareTo(fileSizes[f1] ?: 0L) // Biggest first
                            SortMode.SORT_BY_DATE -> (f2.lastModified()).compareTo(f1.lastModified()) // Newest first
                        }
                    }
            )
            .filter { !excluder.isExcluded(it.getAbsolutePath()) }
            .map { it.toFileTreeNode() }
    }
}
