package com.rk.filetree

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.set

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(
        file = this,
        isFile = isFile(),
        isDirectory = isDirectory(),
        name = getAppropriateName()
    )
}


class FileTreeViewModel : ViewModel() {
    var selectedFile = mutableStateMapOf<FileObject, FileObject>()
    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val expandedNodes = mutableStateMapOf<FileObject, Boolean>()
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
        if (isNodeCut(fileObject)){
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
        if (file.isFile()) {
            throw IllegalStateException("file ${file.getAbsolutePath()} is a file but a directory was expected")
        }
        viewModelScope.launch(Dispatchers.IO) {
            _loadingStates[file] = true  // Mark as loading

            try {
                // Safely access file listing
                val fileList = try {
                    file.listFiles()
                } catch (e: Exception) {
                    _loadingStates[file] = false
                    return@launch
                }

                // Process files
                val files = fileList
                    .sortedWith(compareBy({ !it.isDirectory() }, { it.getName().lowercase() }))
                    .map { it.toFileTreeNode() }

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

    suspend fun refreshEverything() = withContext(Dispatchers.IO){
        fileListCache.keys.toList().forEach {
            updateCache(it)
        }
    }

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
                val fileList = try {
                    node.file.listFiles()
                } catch (e: Exception) {
                    _loadingStates[node.file] = false
                    return@launch
                }

                // Process files
                val files = fileList
                    .sortedWith(compareBy({ !it.isDirectory() }, { it.getName().lowercase() }))
                    .map {
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
