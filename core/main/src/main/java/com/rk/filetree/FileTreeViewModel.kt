package com.rk.filetree

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(file = this, isFile = isFile(), isDirectory = isDirectory(), name = getAppropriateName())
}

class FileTreeViewModel : ViewModel() {
    var selectedFile = mutableStateMapOf<FileObject, FileObject>()
    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val expandedNodes = mutableStateMapOf<FileObject, Boolean>()

    fun getExpandedNodes(): Map<FileObject, Boolean> {
        return mutableMapOf<FileObject, Boolean>().apply { expandedNodes.forEach { set(it.key, it.value) } }
    }

    fun setExpandedNodes(map: Map<FileObject, Boolean>) {
        map.forEach { expandedNodes[it.key] = it.value }
    }

    private val cutNode = mutableStateOf<FileObject?>(null)

    // Track loading states to avoid showing spinners incorrectly
    private val _loadingStates = mutableStateMapOf<FileObject, Boolean>()

    fun isNodeExpanded(fileObject: FileObject): Boolean = expandedNodes[fileObject] == true

    fun isNodeLoading(fileObject: FileObject): Boolean = _loadingStates[fileObject] == true

    fun isNodeCut(fileObject: FileObject): Boolean = cutNode.value == fileObject

    fun markNodeAsCut(fileObject: FileObject) {
        cutNode.value = fileObject
    }

    fun unmarkNodeAsCut(fileObject: FileObject) {
        if (isNodeCut(fileObject)) {
            cutNode.value = null
        }
    }

    fun toggleNodeExpansion(fileObject: FileObject) {
        val wasExpanded = expandedNodes[fileObject] == true
        expandedNodes[fileObject] = !wasExpanded

        // If we're expanding and haven't loaded yet, trigger a load
        if (!wasExpanded && !fileListCache.containsKey(fileObject)) {
            _loadingStates[fileObject] = true
        }
    }

    fun updateCache(file: FileObject) {
        if (file.isDirectory().not()) {
            return
        }
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
                val files =
                    fileList.sortedWith(compareBy({ !it.isDirectory() }, { it.getName().lowercase() })).map {
                        it.toFileTreeNode()
                    }

                fileListCache[file] = files

                // Maybe important
                if (!isNodeExpanded(file)) {
                    expandedNodes[file] = true
                }

                viewModelScope.launch {
                    delay(300)
                    _loadingStates[file] = false
                }
            } catch (e: Exception) {
                _loadingStates[file] = false
            }
        }
    }

    suspend fun goToFolder(projectFile: FileObject, fileObject: FileObject) {
        selectedFile[projectFile] = fileObject

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

            //            //a bit unnecessary but it auto refresh files when loading from cache
            //            viewModelScope.launch(Dispatchers.IO) {
            //                updateCache(node.file)
            //            }
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
                    } catch (e: Exception) {
                        _loadingStates[node.file] = false
                        return@launch
                    }

                // Process files
                val files =
                    fileList.sortedWith(compareBy({ !it.isDirectory() }, { it.getName().lowercase() })).map {
                        it.toFileTreeNode()
                    }

                fileListCache[node.file] = files
                viewModelScope.launch {
                    delay(300)
                    _loadingStates[node.file] = false
                }
            } catch (e: Exception) {
                _loadingStates[node.file] = false
            }
        }
    }
}
