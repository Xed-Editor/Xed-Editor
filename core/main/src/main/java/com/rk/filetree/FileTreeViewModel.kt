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
import com.rk.activities.main.ui.searchViewModel
import com.rk.events.Events
import com.rk.events.FileTreeEvent
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.file.ZipFileObject
import com.rk.search.utils.GlobExcluder
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(
        file = this,
        isFile = isFile(),
        isExpandable = isDirectory() || isZip(),
        name = getAppropriateName(),
    )
}

fun FileObject.isZip(): Boolean {
    return isFile() && getExtension().equals("zip", ignoreCase = true)
}

fun FileObject.isXedPackage(): Boolean {
    return isFile() && getExtension().equals("xed", ignoreCase = true)
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
    var isRefreshing by mutableStateOf(false)
        private set

    private val selectedFiles = mutableStateMapOf<FileObject, List<FileObject>>()
    private val focusedFile = mutableStateMapOf<FileObject, FileObject>()
    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val expandedNodes = mutableStateMapOf<FileObject, Set<FileObject>>()
    private val collapsedNameCache = mutableStateMapOf<FileObject, String>()
    private var fileOperationsCount by mutableIntStateOf(0)

    private val excluder by derivedStateOf { GlobExcluder(Settings.excluded_files_drawer) }

    fun getExpandedNodes(): Map<FileObject, Set<FileObject>> {
        // Convert to java `Set` to make serialization possible
        return expandedNodes.mapValues { (_, value) -> HashSet(value) }
    }

    fun setExpandedNodes(map: Map<FileObject, Set<FileObject>>) {
        map.forEach { (key, value) -> expandedNodes[key] = expandedNodes[key]?.plus(value) ?: value }
    }

    fun toggleSelection(projectRoot: FileObject, fileObject: FileObject) {
        if (isFileSelected(projectRoot, fileObject)) {
            unselectFile(projectRoot, fileObject)
        } else {
            selectFile(projectRoot, fileObject)
        }
        viewModelScope.launch {
            Events.publish(FileTreeEvent.SelectionChanged(projectRoot, getSelectedFiles(projectRoot)))
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
        viewModelScope.launch {
            Events.publish(FileTreeEvent.SelectionChanged(projectRoot, emptyList()))
        }
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

    @XedExtensionPoint
    suspend fun withFileOperation(block: suspend () -> Unit) {
        registerFileOperation()
        try {
            block()
        } finally {
            unregisterFileOperation()
        }
    }

    private fun registerFileOperation() {
        fileOperationsCount++
    }

    private fun unregisterFileOperation() {
        if (fileOperationsCount > 0) {
            fileOperationsCount--
        }
    }

    fun isFileOperationInProgress(): Boolean {
        return fileOperationsCount > 0
    }

    private val cutNodes = mutableStateListOf<FileObject>()

    // File -> Error severity (see DiagnosticRegion.java)
    private val diagnosedNodes = mutableStateMapOf<FileObject, Int>()

    // Track loading states to avoid showing spinners incorrectly
    private val _loadingStates = mutableStateMapOf<FileObject, Boolean>()

    fun isNodeExpanded(projectRoot: FileObject, fileObject: FileObject): Boolean =
        expandedNodes[projectRoot]?.contains(fileObject) ?: false

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

    fun toggleNodeExpansion(projectRoot: FileObject, fileObject: FileObject) {
        val wasExpanded = isNodeExpanded(projectRoot, fileObject)
        if (wasExpanded) {
            collapseFile(projectRoot, fileObject)
        } else {
            expandFile(projectRoot, fileObject)
        }
    }

    private fun collapseFile(projectRoot: FileObject, fileObject: FileObject) {
        expandedNodes[projectRoot] = expandedNodes[projectRoot]?.minus(fileObject) ?: emptySet()
        if (expandedNodes[projectRoot]?.isEmpty() == true) {
            expandedNodes.remove(projectRoot)
        }
        viewModelScope.launch { Events.publish(FileTreeEvent.NodeCollapsed(projectRoot, fileObject)) }
    }

    private fun expandFile(projectRoot: FileObject, fileObject: FileObject) {
        expandedNodes[projectRoot] = expandedNodes[projectRoot]?.plus(fileObject) ?: setOf(fileObject)

        // If we're expanding and haven't loaded yet, trigger a load
        if (!fileListCache.containsKey(fileObject)) {
            _loadingStates[fileObject] = true
        }
        viewModelScope.launch { Events.publish(FileTreeEvent.NodeExpanded(projectRoot, fileObject)) }
    }

    fun getCollapsedName(node: FileTreeNode): String {
        return collapsedNameCache[node.file] ?: node.name
    }

    suspend fun collapseNode(projectFile: FileObject, node: FileTreeNode): FileTreeNode {
        var currentNode = node
        var collapsedName = node.name
        while (true) {
            expandFile(projectFile, currentNode.file)
            loadChildrenForNodeSynchronous(currentNode)
            val children = getNodeChildren(currentNode)
            if (children.size != 1) {
                break
            }
            val child = children.first()
            if (!child.isExpandable) {
                break
            }
            collapsedName += "/${child.name}"
            currentNode = child
        }
        collapsedNameCache[node.file] = collapsedName
        return currentNode
    }

    fun updateCache(parent: FileObject) {
        if (!parent.isDirectory() && !parent.isZip()) {
            return
        }
        searchViewModel.get()?.syncIndex(parent)

        viewModelScope.launch {
            Events.publish(FileTreeEvent.TreeSynchronized(parent))
        }

        collapsedNameCache.remove(parent)
        _loadingStates[parent] = true // Mark as loading
        viewModelScope.launch(Dispatchers.IO) {
            loadAndCacheChildren(parent)
        }
    }

    fun isFileFocused(projectFile: FileObject, fileObject: FileObject) = focusedFile[projectFile] == fileObject

    suspend fun goToFolder(projectFile: FileObject, fileObject: FileObject) {
        focusedFile[projectFile] = fileObject
        viewModelScope.launch {
            Events.publish(FileTreeEvent.Focused(projectFile, fileObject))
            delay(1000.milliseconds)
            focusedFile.remove(projectFile)
        }

        var currentFile: FileObject? = fileObject
        while (currentFile != null && currentFile != projectFile) {
            expandFile(projectFile, currentFile)

            // If we're expanding and haven't loaded yet, trigger a load
            if (!fileListCache.containsKey(currentFile)) {
                _loadingStates[currentFile] = true
            }

            currentFile = currentFile.getParentFile()
        }

        expandFile(projectFile, projectFile)
    }

    suspend fun refreshEverything(wasPulled: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (wasPulled) isRefreshing = true
            fileListCache.keys.toList().forEach { updateCache(it) }
            isRefreshing = false
        }

    fun getNodeChildren(node: FileTreeNode): List<FileTreeNode> {
        return fileListCache[node.file] ?: emptyList()
    }

    fun loadChildrenForNode(node: FileTreeNode) {
        // If already in cache, don't reload
        val file = node.file
        if (fileListCache.containsKey(file)) {
            _loadingStates[file] = false
            return
        }

        // Set loading state
        _loadingStates[file] = true

        viewModelScope.launch(Dispatchers.IO) {
            loadAndCacheChildren(file)
        }
    }

    suspend fun loadChildrenForNodeSynchronous(node: FileTreeNode) {
        // If already in cache, don't reload
        val file = node.file
        if (fileListCache.containsKey(file)) {
            _loadingStates[file] = false
            return
        }

        // Set loading state
        _loadingStates[file] = true

        loadAndCacheChildren(file)
    }

    private suspend fun loadAndCacheChildren(file: FileObject) {
        try {
            // Safely access file listing
            val fileList =
                try {
                    val effectiveFile = toZipAwareFile(file)
                    effectiveFile.listFiles()
                } catch (_: Exception) {
                    _loadingStates[file] = false
                    return
                }

            // Process files
            val sortedFiles = sortAndFilterFiles(fileList)

            fileListCache[file] = sortedFiles
            viewModelScope.launch { clearLoadingState(file) }
        } catch (_: Exception) {
            _loadingStates[file] = false
        }
    }

    private fun toZipAwareFile(file: FileObject): FileObject {
        val effectiveFile =
            if (file !is ZipFileObject && file.isZip()) {
                ZipFileObject(file, "")
            } else {
                file
            }
        return effectiveFile
    }

    private suspend fun clearLoadingState(file: FileObject) {
        // Use delay to avoid flickering
        delay(300.milliseconds)
        _loadingStates[file] = false
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

    private suspend fun calculateLastModifiedDates(fileObjects: List<FileObject>): Map<FileObject, Long> {
        if (sortMode != SortMode.SORT_BY_DATE) return emptyMap()

        return fileObjects.associateWith { it.lastModified() ?: 0L }
    }

    private suspend fun sortAndFilterFiles(fileObjects: List<FileObject>): List<FileTreeNode> {
        val fileSizes = calculateFileSizes(fileObjects)
        val lastModifiedDates = calculateLastModifiedDates(fileObjects)

        return fileObjects
            .sortedWith(
                compareBy<FileObject> { !it.isDirectory() }
                    .thenComparator { f1, f2 ->
                        when (sortMode) {
                            SortMode.SORT_BY_NAME ->
                                f1.getName().lowercase().compareTo(f2.getName().lowercase()) // A -> Z
                            SortMode.SORT_BY_SIZE ->
                                (fileSizes[f2] ?: 0L).compareTo(fileSizes[f1] ?: 0L) // Biggest first
                            SortMode.SORT_BY_DATE ->
                                (lastModifiedDates[f2] ?: 0L).compareTo(lastModifiedDates[f1] ?: 0L) // Newest first
                        }
                    }
            )
            .filter { !excluder.isExcluded(it.getAbsolutePath()) }
            .map { it.toFileTreeNode() }
    }
}
