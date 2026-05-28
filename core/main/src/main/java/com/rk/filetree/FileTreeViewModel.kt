package com.rk.filetree

import androidx.lifecycle.ViewModel
import com.rk.file.FileObject

class FileTreeViewModel : ViewModel() {
    val dialogState = FileTreeDialogState()
    val selectionManager = FileTreeSelectionManager()
    val cacheManager = FileTreeCacheManager(viewModelScope)

    // ─── Dialog State Accessors ───────────────────────────────────────
    var showRenameDialog
        get() = dialogState.showRenameDialog
        set(v) { dialogState.showRenameDialog = v }
    var renameFile
        get() = dialogState.renameFile
        set(v) { dialogState.renameFile = v }
    var renameValue
        get() = dialogState.renameValue
        set(v) { dialogState.renameValue = v }
    var renameError
        get() = dialogState.renameError
        set(v) { dialogState.renameError = v }
    var showDeleteConfirmation
        get() = dialogState.showDeleteConfirmation
        set(v) { dialogState.showDeleteConfirmation = v }
    var deleteFiles
        get() = dialogState.deleteFiles
        set(v) { dialogState.deleteFiles = v }
    var deleteRoot
        get() = dialogState.deleteRoot
        set(v) { dialogState.deleteRoot = v }
    var showPropertiesDialog
        get() = dialogState.showPropertiesDialog
        set(v) { dialogState.showPropertiesDialog = v }
    var propertyFile
        get() = dialogState.propertyFile
        set(v) { dialogState.propertyFile = v }
    var showCreateDialog
        get() = dialogState.showCreateDialog
        set(v) { dialogState.showCreateDialog = v }
    var isCreateFile
        get() = dialogState.isCreateFile
        set(v) { dialogState.isCreateFile = v }
    var createValue
        get() = dialogState.createValue
        set(v) { dialogState.createValue = v }
    var createError
        get() = dialogState.createError
        set(v) { dialogState.createError = v }
    var createParentFile
        get() = dialogState.createParentFile
        set(v) { dialogState.createParentFile = v }
    var createRoot
        get() = dialogState.createRoot
        set(v) { dialogState.createRoot = v }
    var showCloseProjectConfirmation
        get() = dialogState.showCloseProjectConfirmation
        set(v) { dialogState.showCloseProjectConfirmation = v }
    var projectConfirmationRoot
        get() = dialogState.projectConfirmationRoot
        set(v) { dialogState.projectConfirmationRoot = v }

    // ─── Cache State Accessors ────────────────────────────────────────
    val sortMode get() = cacheManager.sortMode
    val expandedNodes: Map<FileObject, Boolean> get() = selectionManager.expandedNodes

    // ─── Dialog Methods ───────────────────────────────────────────────
    fun showRenameDialog(file: FileObject) = dialogState.showRenameDialog(file)
    fun closeRenameDialog() = dialogState.closeRenameDialog()
    fun showDeleteConfirmation(files: List<FileObject>, root: FileObject?) = dialogState.showDeleteConfirmation(files, root)
    fun closeDeleteConfirmation() = dialogState.closeDeleteConfirmation()
    fun showPropertiesDialog(file: FileObject) = dialogState.showPropertiesDialog(file)
    fun closePropertiesDialog() = dialogState.closePropertiesDialog()
    fun showCreateDialog(isCreateFile: Boolean, parentFile: FileObject, root: FileObject?) = dialogState.showCreateDialog(isCreateFile, parentFile, root)
    fun closeCreateDialog() = dialogState.closeCreateDialog()
    fun showCloseProjectConfirmation(root: FileObject) = dialogState.showCloseProjectConfirmation(root)
    fun closeCloseProjectConfirmation() = dialogState.closeCloseProjectConfirmation()

    // ─── Selection Methods ────────────────────────────────────────────
    fun getExpandedNodes(): Map<FileObject, Boolean> = selectionManager.getExpandedNodes()
    fun setExpandedNodes(map: Map<FileObject, Boolean>) = selectionManager.setExpandedNodes(map)
    fun toggleSelection(projectRoot: FileObject, fileObject: FileObject) = selectionManager.toggleSelection(projectRoot, fileObject)
    fun selectFile(projectRoot: FileObject, fileObject: FileObject) = selectionManager.selectFile(projectRoot, fileObject)
    fun unselectFile(projectRoot: FileObject, fileObject: FileObject) = selectionManager.unselectFile(projectRoot, fileObject)
    fun unselectAllFiles(projectRoot: FileObject) = selectionManager.unselectAllFiles(projectRoot)
    fun isFileSelected(projectRoot: FileObject, fileObject: FileObject): Boolean = selectionManager.isFileSelected(projectRoot, fileObject)
    fun isAnyFileSelected(projectRoot: FileObject): Boolean = selectionManager.isAnyFileSelected(projectRoot)
    fun getSelectionCount(projectRoot: FileObject): Int = selectionManager.getSelectionCount(projectRoot)
    fun getSelectedFiles(projectRoot: FileObject): List<FileObject> = selectionManager.getSelectedFiles(projectRoot)
    fun isNodeCut(fileObject: FileObject): Boolean = selectionManager.isNodeCut(fileObject)
    fun markNodeAsCut(fileObject: FileObject) = selectionManager.markNodeAsCut(fileObject)
    fun unmarkNodeAsCut(fileObject: FileObject) = selectionManager.unmarkNodeAsCut(fileObject)
    fun diagnoseNode(fileObject: FileObject, severity: Int) = selectionManager.diagnoseNode(fileObject, severity)
    fun undiagnoseNode(fileObject: FileObject) = selectionManager.undiagnoseNode(fileObject)
    fun getNodeSeverity(fileObject: FileObject): Int = selectionManager.getNodeSeverity(fileObject)
    fun isNodeExpanded(fileObject: FileObject): Boolean = selectionManager.isNodeExpanded(fileObject)

    fun toggleNodeExpansion(fileObject: FileObject) {
        val nowExpanded = selectionManager.toggleNodeExpansion(fileObject)
        cacheManager.onExpanding(fileObject, nowExpanded)
    }

    fun isFileFocused(projectFile: FileObject, fileObject: FileObject): Boolean = selectionManager.isFileFocused(projectFile, fileObject)

    suspend fun goToFolder(projectFile: FileObject, fileObject: FileObject) {
        selectionManager.focusFile(viewModelScope, projectFile, fileObject)
        selectionManager.expandToRoot(fileObject, projectFile, cacheManager::isInCache)
    }

    // ─── Cache Methods ────────────────────────────────────────────────
    fun isNodeLoading(fileObject: FileObject): Boolean = cacheManager.isNodeLoading(fileObject)
    fun getNodeChildren(node: FileTreeNode): List<FileTreeNode> = cacheManager.getNodeChildren(node)
    fun getCollapsedName(node: FileTreeNode): String = cacheManager.getCollapsedName(node)
    fun isFileOperationInProgress(): Boolean = cacheManager.isFileOperationInProgress()
    fun registerFileOperation() = cacheManager.registerFileOperation()
    fun unregisterFileOperation() = cacheManager.unregisterFileOperation()
    suspend fun withFileOperation(block: suspend () -> Unit) = cacheManager.withFileOperation(block)

    suspend fun collapseNode(node: FileTreeNode): FileTreeNode = cacheManager.collapseNode(
        node,
        isExpanded = selectionManager::isNodeExpanded,
        expand = selectionManager::expandNode
    )

    fun updateCache(file: FileObject) = cacheManager.updateCache(file)
    fun loadChildrenForNode(node: FileTreeNode) = cacheManager.loadChildrenForNode(node)
    suspend fun loadChildrenForNodeSynchronous(node: FileTreeNode) = cacheManager.loadChildrenForNodeSynchronous(node)
    suspend fun refreshEverything() = cacheManager.refreshEverything()
}
