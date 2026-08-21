package com.rk.filetree

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.ui.searchViewModel
import com.rk.events.Events
import com.rk.events.FileTreeEvent
import com.rk.extension.ActivityProvider
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.file.ZipFileObject
import com.rk.search.utils.GlobExcluder
import com.rk.settings.Settings
import com.rk.utils.LoadingPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog = _showRenameDialog.asStateFlow()

    private val _renameFile = MutableStateFlow<FileObject?>(null)
    val renameFile = _renameFile.asStateFlow()

    private val _renameValue = MutableStateFlow("")
    val renameValue = _renameValue.asStateFlow()

    private val _renameError = MutableStateFlow<String?>(null)
    val renameError = _renameError.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation = _showDeleteConfirmation.asStateFlow()

    private val _deleteFiles = MutableStateFlow<List<FileObject>?>(null)
    val deleteFiles = _deleteFiles.asStateFlow()

    private val _deleteRoot = MutableStateFlow<FileObject?>(null)
    val deleteRoot = _deleteRoot.asStateFlow()

    private val _showPropertiesDialog = MutableStateFlow(false)
    val showPropertiesDialog = _showPropertiesDialog.asStateFlow()

    private val _propertyFile = MutableStateFlow<FileObject?>(null)
    val propertyFile = _propertyFile.asStateFlow()

    private val _isCreateFile = MutableStateFlow(true)
    val isCreateFile = _isCreateFile.asStateFlow()

    private val _createValue = MutableStateFlow("")
    val createValue = _createValue.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError = _createError.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog = _showCreateDialog.asStateFlow()

    private val _createParentFile = MutableStateFlow<FileObject?>(null)
    val createParentFile = _createParentFile.asStateFlow()

    private val _createRoot = MutableStateFlow<FileObject?>(null)
    val createRoot = _createRoot.asStateFlow()

    private val _showCloseProjectConfirmation = MutableStateFlow(false)
    val showCloseProjectConfirmation = _showCloseProjectConfirmation.asStateFlow()

    private val _projectConfirmationRoot = MutableStateFlow<FileObject?>(null)
    val projectConfirmationRoot = _projectConfirmationRoot.asStateFlow()

    fun setRenameValue(value: String) {
        _renameValue.value = value
    }

    fun setRenameError(value: String?) {
        _renameError.value = value
    }

    fun setCreateValue(value: String) {
        _createValue.value = value
    }

    fun setCreateError(value: String?) {
        _createError.value = value
    }

    fun setSortMode(value: SortMode) {
        _sortMode.value = value
    }

    fun showRenameDialog(file: FileObject) {
        _showRenameDialog.value = true
        _renameValue.value = file.getName()
        _renameFile.value = file
    }

    fun closeRenameDialog() {
        _showRenameDialog.value = false
        _renameValue.value = ""
        _renameError.value = null
        _renameFile.value = null
    }

    fun showDeleteConfirmation(files: List<FileObject>, root: FileObject?) {
        _showDeleteConfirmation.value = true
        _deleteFiles.value = files
        _deleteRoot.value = root
    }

    fun closeDeleteConfirmation() {
        _showDeleteConfirmation.value = false
        _deleteFiles.value = null
        _deleteRoot.value = null
    }

    fun showPropertiesDialog(file: FileObject) {
        _showPropertiesDialog.value = true
        _propertyFile.value = file
    }

    fun closePropertiesDialog() {
        _showPropertiesDialog.value = false
        _propertyFile.value = null
    }

    fun showCreateDialog(isCreateFile: Boolean, parentFile: FileObject, root: FileObject?) {
        _isCreateFile.value = isCreateFile
        _showCreateDialog.value = true
        _createParentFile.value = parentFile
        _createRoot.value = root
    }

    fun closeCreateDialog() {
        _showCreateDialog.value = false
        _createError.value = null
        _createParentFile.value = null
        _createRoot.value = null
    }

    fun showCloseProjectConfirmation(root: FileObject) {
        _showCloseProjectConfirmation.value = true
        _projectConfirmationRoot.value = root
    }

    fun closeCloseProjectConfirmation() {
        _showCloseProjectConfirmation.value = false
        _projectConfirmationRoot.value = null
    }

    // File tree
    private val _sortMode = MutableStateFlow(SortMode.entries[Settings.sort_mode])
    val sortMode = _sortMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Map<FileObject, List<FileObject>>>(emptyMap())
    val selectedFiles = _selectedFiles.asStateFlow()

    private val _focusedFile = MutableStateFlow<Map<FileObject, FileObject>>(emptyMap())
    val focusedFile = _focusedFile.asStateFlow()

    private val _fileListCache = MutableStateFlow<Map<FileObject, List<FileTreeNode>>>(emptyMap())
    val fileListCache = _fileListCache.asStateFlow()

    private val _expandedNodes = MutableStateFlow<Map<FileObject, Set<FileObject>>>(emptyMap())
    val expandedNodes = _expandedNodes.asStateFlow()

    private val _collapsedNameCache = MutableStateFlow<Map<FileObject, String>>(emptyMap())
    val collapsedNameCache = _collapsedNameCache.asStateFlow()

    private val _fileOperationsCount = MutableStateFlow(0)
    val fileOperationsCount = _fileOperationsCount.asStateFlow()

    private val excluder: GlobExcluder
        get() = GlobExcluder(Settings.excluded_files_drawer)

    fun getExpandedNodes(): Map<FileObject, Set<FileObject>> {
        // Convert to java `Set` to make serialization possible
        return _expandedNodes.value.mapValues { (_, value) -> HashSet(value) }
    }

    fun setExpandedNodes(map: Map<FileObject, Set<FileObject>>) {
        _expandedNodes.update { current ->
            map.entries.fold(current) { acc, (key, value) -> acc + (key to (acc[key]?.plus(value) ?: value)) }
        }
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
        _selectedFiles.update { map ->
            map + (projectRoot to ((map[projectRoot] ?: emptyList()) + fileObject))
        }
    }

    fun unselectFile(projectRoot: FileObject, fileObject: FileObject) {
        _selectedFiles.update { map ->
            val newList = (map[projectRoot] ?: emptyList()) - fileObject
            if (newList.isEmpty()) map - projectRoot else map + (projectRoot to newList)
        }
    }

    fun unselectAllFiles(projectRoot: FileObject) {
        _selectedFiles.update { it - projectRoot }
        viewModelScope.launch {
            Events.publish(FileTreeEvent.SelectionChanged(projectRoot, emptyList()))
        }
    }

    fun isFileSelected(projectRoot: FileObject, fileObject: FileObject): Boolean {
        return _selectedFiles.value[projectRoot]?.contains(fileObject) == true
    }

    fun isAnyFileSelected(projectRoot: FileObject): Boolean {
        return _selectedFiles.value[projectRoot]?.isNotEmpty() == true
    }

    fun getSelectionCount(projectRoot: FileObject): Int {
        return _selectedFiles.value[projectRoot]?.size ?: 0
    }

    fun getSelectedFiles(projectRoot: FileObject): List<FileObject> {
        return _selectedFiles.value[projectRoot] ?: emptyList()
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
        _fileOperationsCount.update { it + 1 }
    }

    private fun unregisterFileOperation() {
        _fileOperationsCount.update { if (it > 0) it - 1 else it }
    }

    fun isFileOperationInProgress(): Boolean {
        return _fileOperationsCount.value > 0
    }

    private val _cutNodes = MutableStateFlow<List<FileObject>>(emptyList())
    val cutNodes = _cutNodes.asStateFlow()

    // File -> Error severity (see DiagnosticRegion.java)
    private val _diagnosedNodes = MutableStateFlow<Map<FileObject, Int>>(emptyMap())
    val diagnosedNodes = _diagnosedNodes.asStateFlow()

    // Track loading states to avoid showing spinners incorrectly
    private val _loadingStates = MutableStateFlow<Map<FileObject, Boolean>>(emptyMap())
    val loadingStates = _loadingStates.asStateFlow()

    fun isNodeExpanded(projectRoot: FileObject, fileObject: FileObject): Boolean =
        _expandedNodes.value[projectRoot]?.contains(fileObject) ?: false

    fun isNodeLoading(fileObject: FileObject): Boolean = _loadingStates.value[fileObject] == true

    fun isNodeCut(fileObject: FileObject): Boolean = _cutNodes.value.contains(fileObject)

    fun markNodeAsCut(fileObject: FileObject) {
        _cutNodes.update { it + fileObject }
    }

    fun unmarkNodeAsCut(fileObject: FileObject) {
        _cutNodes.update { it - fileObject }
    }

    fun diagnoseNode(fileObject: FileObject, severity: Int) {
        _diagnosedNodes.update { it + (fileObject to severity) }
    }

    fun undiagnoseNode(fileObject: FileObject) {
        _diagnosedNodes.update { it - fileObject }
    }

    fun getNodeSeverity(fileObject: FileObject): Int {
        return _diagnosedNodes.value[fileObject] ?: -1
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
        _expandedNodes.update { map ->
            val newSet = (map[projectRoot] ?: emptySet()) - fileObject
            if (newSet.isEmpty()) map - projectRoot else map + (projectRoot to newSet)
        }
        viewModelScope.launch { Events.publish(FileTreeEvent.NodeCollapsed(projectRoot, fileObject)) }
    }

    private fun expandFile(projectRoot: FileObject, fileObject: FileObject) {
        _expandedNodes.update { map ->
            map + (projectRoot to ((map[projectRoot] ?: emptySet()) + fileObject))
        }

        // If we're expanding and haven't loaded yet, trigger a load
        if (!_fileListCache.value.containsKey(fileObject)) {
            _loadingStates.update { it + (fileObject to true) }
        }
        viewModelScope.launch { Events.publish(FileTreeEvent.NodeExpanded(projectRoot, fileObject)) }
    }

    fun getCollapsedName(node: FileTreeNode): String {
        return _collapsedNameCache.value[node.file] ?: node.name
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
        _collapsedNameCache.update { it + (node.file to collapsedName) }
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

        _collapsedNameCache.update { it - parent }
        _loadingStates.update { it + (parent to true) } // Mark as loading
        viewModelScope.launch(Dispatchers.IO) {
            loadAndCacheChildren(parent)
        }
    }

    fun isFileFocused(projectFile: FileObject, fileObject: FileObject) = _focusedFile.value[projectFile] == fileObject

    suspend fun goToFolder(projectFile: FileObject, fileObject: FileObject) {
        _focusedFile.update { it + (projectFile to fileObject) }
        viewModelScope.launch {
            Events.publish(FileTreeEvent.Focused(projectFile, fileObject))
            delay(1000.milliseconds)
            _focusedFile.update { it - projectFile }
        }

        var currentFile: FileObject? = fileObject
        while (currentFile != null && currentFile != projectFile) {
            expandFile(projectFile, currentFile)

            // If we're expanding and haven't loaded yet, trigger a load
            if (!_fileListCache.value.containsKey(currentFile)) {
                _loadingStates.update { it + (currentFile to true) }
            }

            currentFile = currentFile.getParentFile()
        }

        expandFile(projectFile, projectFile)
    }

    suspend fun refreshEverything(wasPulled: Boolean = false) =
        withContext(Dispatchers.IO) {
            if (wasPulled) _isRefreshing.value = true
            _fileListCache.value.keys.toList().forEach { updateCache(it) }
            _isRefreshing.value = false
        }

    fun getNodeChildren(node: FileTreeNode): List<FileTreeNode> {
        return _fileListCache.value[node.file] ?: emptyList()
    }

    fun loadChildrenForNode(node: FileTreeNode) {
        // If already in cache, don't reload
        val file = node.file
        if (_fileListCache.value.containsKey(file)) {
            _loadingStates.update { it + (file to false) }
            return
        }

        // Set loading state
        _loadingStates.update { it + (file to true) }

        viewModelScope.launch(Dispatchers.IO) {
            loadAndCacheChildren(file)
        }
    }

    suspend fun loadChildrenForNodeSynchronous(node: FileTreeNode) {
        // If already in cache, don't reload
        val file = node.file
        if (_fileListCache.value.containsKey(file)) {
            _loadingStates.update { it + (file to false) }
            return
        }

        // Set loading state
        _loadingStates.update { it + (file to true) }

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
                    _loadingStates.update { it + (file to false) }
                    return
                }

            // Process files
            val sortedFiles = sortAndFilterFiles(fileList)

            _fileListCache.update { it + (file to sortedFiles) }
            viewModelScope.launch { clearLoadingState(file) }
        } catch (_: Exception) {
            _loadingStates.update { it + (file to false) }
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
        _loadingStates.update { it + (file to false) }
    }

    private suspend fun calculateFileSizes(fileObjects: List<FileObject>): Map<FileObject, Long> {
        val fileSizes = mutableMapOf<FileObject, Long>()
        if (sortMode.value != SortMode.SORT_BY_SIZE) return fileSizes

        fileObjects.forEach { file ->
            if (!file.isDirectory()) {
                fileSizes[file] = file.length()
            }
        }
        return fileSizes
    }

    private suspend fun calculateLastModifiedDates(fileObjects: List<FileObject>): Map<FileObject, Long> {
        if (sortMode.value != SortMode.SORT_BY_DATE) return emptyMap()

        return fileObjects.associateWith { it.lastModified() ?: 0L }
    }

    private suspend fun sortAndFilterFiles(fileObjects: List<FileObject>): List<FileTreeNode> {
        val fileSizes = calculateFileSizes(fileObjects)
        val lastModifiedDates = calculateLastModifiedDates(fileObjects)

        return fileObjects
            .sortedWith(
                compareBy<FileObject> { !it.isDirectory() }
                    .thenComparator { f1, f2 ->
                        when (sortMode.value) {
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
