package com.rk.filetree

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.activities.main.gitViewModel
import com.rk.activities.main.searchViewModel
import com.rk.file.FileObject
import com.rk.search.GlobExcluder
import com.rk.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(file = this, isFile = isFile(), isDirectory = isDirectory(), name = getAppropriateName())
}

class FileTreeCacheManager(
    private val scope: CoroutineScope
) {
    var sortMode by mutableStateOf(SortMode.entries[Settings.sort_mode])

    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val collapsedNameCache = mutableStateMapOf<FileObject, String>()
    private val _loadingStates = mutableStateMapOf<FileObject, Boolean>()
    private var fileOperationsCount by mutableIntStateOf(0)

    private val excluder by derivedStateOf { GlobExcluder(Settings.excluded_files_drawer) }

    fun isNodeLoading(fileObject: FileObject): Boolean = _loadingStates[fileObject] == true

    fun getNodeChildren(node: FileTreeNode): List<FileTreeNode> {
        return fileListCache[node.file] ?: emptyList()
    }

    fun getCollapsedName(node: FileTreeNode): String {
        return collapsedNameCache[node.file] ?: node.name
    }

    fun isFileOperationInProgress(): Boolean = fileOperationsCount > 0

    fun registerFileOperation() { fileOperationsCount++ }
    fun unregisterFileOperation() { fileOperationsCount-- }

    suspend fun withFileOperation(block: suspend () -> Unit) {
        registerFileOperation()
        try { block() } finally { unregisterFileOperation() }
    }

    fun isInCache(file: FileObject): Boolean = fileListCache.containsKey(file)

    fun updateCache(file: FileObject) {
        searchViewModel.get()?.syncIndex(file)
        gitViewModel.get()?.syncChanges(file.getAbsolutePath())
        if (!file.isDirectory()) return

        collapsedNameCache.remove(file)
        _loadingStates[file] = true

        scope.launch(Dispatchers.IO) {
            try {
                val fileList = runCatching { file.listFiles() }.getOrElse {
                    scope.launch(Dispatchers.Main) { _loadingStates[file] = false }
                    return@launch
                }
                val sortedFiles = sortAndFilterFiles(fileList)
                scope.launch(Dispatchers.Main) {
                    fileListCache[file] = sortedFiles
                    scope.launch { delay(300); _loadingStates[file] = false }
                }
                } catch (e: Exception) {
                    Log.e("FileTreeCache", "updateCache failed for ${file.getAbsolutePath()}", e)
                    scope.launch(Dispatchers.Main) { _loadingStates[file] = false }
            }
        }
    }

    fun loadChildrenForNode(node: FileTreeNode) {
        if (fileListCache.containsKey(node.file)) {
            _loadingStates[node.file] = false
            return
        }
        _loadingStates[node.file] = true

        scope.launch(Dispatchers.IO) {
            try {
                val fileList = runCatching { node.file.listFiles() }.getOrElse {
                    scope.launch(Dispatchers.Main) { _loadingStates[node.file] = false }
                    return@launch
                }
                val sortedFiles = sortAndFilterFiles(fileList)
                scope.launch(Dispatchers.Main) {
                    fileListCache[node.file] = sortedFiles
                    scope.launch { delay(300); _loadingStates[node.file] = false }
                }
            } catch (e: Exception) {
                Log.e("FileTreeCache", "loadChildrenForNode failed for ${node.file.getAbsolutePath()}", e)
                scope.launch(Dispatchers.Main) { _loadingStates[node.file] = false }
            }
        }
    }

    suspend fun loadChildrenForNodeSynchronous(node: FileTreeNode) {
        withContext(Dispatchers.Main) {
            if (fileListCache.containsKey(node.file)) {
                _loadingStates[node.file] = false
                return@withContext
            }
            _loadingStates[node.file] = true
        }

        try {
            val fileList = runCatching { node.file.listFiles() }.getOrElse {
                withContext(Dispatchers.Main) { _loadingStates[node.file] = false }
                return
            }
            val sortedFiles = sortAndFilterFiles(fileList)
            withContext(Dispatchers.Main) {
                fileListCache[node.file] = sortedFiles
                scope.launch { delay(300); _loadingStates[node.file] = false }
            }
        } catch (e: Exception) {
            Log.e("FileTreeCache", "loadChildrenForNodeSynchronous failed for ${node.file.getAbsolutePath()}", e)
            withContext(Dispatchers.Main) { _loadingStates[node.file] = false }
        }
    }

    fun onExpanding(fileObject: FileObject, isExpanding: Boolean) {
        if (isExpanding && !fileListCache.containsKey(fileObject)) {
            _loadingStates[fileObject] = true
        }
    }

    suspend fun collapseNode(node: FileTreeNode, isExpanded: (FileObject) -> Boolean, expand: (FileObject) -> Unit): FileTreeNode {
        var currentNode = node
        var collapsedName = node.name
        while (true) {
            if (!isExpanded(currentNode.file)) {
                expand(currentNode.file)
            }
            loadChildrenForNodeSynchronous(currentNode)
            val children = getNodeChildren(currentNode)
            if (children.size != 1) break
            val child = children.first()
            if (!child.isDirectory) break
            collapsedName += "/${child.name}"
            currentNode = child
        }
        collapsedNameCache[node.file] = collapsedName
        return currentNode
    }

    suspend fun refreshEverything() {
        withContext(Dispatchers.IO) {
            fileListCache.keys.toList().forEach { updateCache(it) }
        }
    }

    private suspend fun calculateFileSizes(fileObjects: List<FileObject>): Map<FileObject, Long> {
        if (sortMode != SortMode.SORT_BY_SIZE) return emptyMap()
        return fileObjects
            .filter { !it.isDirectory() }
            .associateWith { it.length() }
    }

    private suspend fun sortAndFilterFiles(fileObjects: List<FileObject>): List<FileTreeNode> {
        val fileSizes = calculateFileSizes(fileObjects)

        return fileObjects
            .sortedWith(
                compareBy<FileObject> { !it.isDirectory() }
                    .thenComparator { f1, f2 ->
                        when (sortMode) {
                            SortMode.SORT_BY_NAME ->
                                f1.getName().lowercase().compareTo(f2.getName().lowercase())
                            SortMode.SORT_BY_SIZE ->
                                (fileSizes[f2] ?: 0L).compareTo(fileSizes[f1] ?: 0L)
                            SortMode.SORT_BY_DATE ->
                                (f2.lastModified()).compareTo(f1.lastModified())
                        }
                    }
            )
            .filter { !excluder.isExcluded(it.getAbsolutePath()) }
            .map { it.toFileTreeNode() }
    }
}
