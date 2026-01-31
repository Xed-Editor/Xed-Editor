package com.rk.filetree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.gitViewModel
import com.rk.file.FileObject
import com.rk.git.GitChange
import com.rk.settings.Settings
import com.rk.utils.findGitRoot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun FileObject.toFileTreeNode(): FileTreeNode {
    return FileTreeNode(file = this, isFile = isFile(), isDirectory = isDirectory(), name = getAppropriateName())
}

class FileTreeViewModel : ViewModel() {
    var sortMode by mutableStateOf(SortMode.entries[Settings.sort_mode])
    var selectedFile = mutableStateMapOf<FileObject, FileObject>()
    private val fileListCache = mutableStateMapOf<FileObject, List<FileTreeNode>>()
    private val expandedNodes = mutableStateMapOf<FileObject, Boolean>()
    var gitChanges by mutableStateOf<List<GitChange>>(emptyList())

    fun getExpandedNodes(): Map<FileObject, Boolean> {
        return mutableMapOf<FileObject, Boolean>().apply { expandedNodes.forEach { set(it.key, it.value) } }
    }

    fun setExpandedNodes(map: Map<FileObject, Boolean>) {
        map.forEach { expandedNodes[it.key] = it.value }
    }

    private val cutNode = mutableStateOf<FileObject?>(null)

    // File -> Error severity (see DiagnosticRegion.java)
    private val diagnosedNodes = mutableStateMapOf<FileObject, Int>()

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

    fun diagnoseNode(fileObject: FileObject, severity: Int) {
        diagnosedNodes[fileObject] = severity
    }

    fun undiagnoseNode(fileObject: FileObject) {
        diagnosedNodes.remove(fileObject)
    }

    fun getNodeSeverity(fileObject: FileObject): Int {
        return diagnosedNodes[fileObject] ?: -1
    }

    fun getGitChange(fileObject: FileObject): GitChange? {
        return gitChanges.find { change -> fileObject.getAbsolutePath().contains(change.path) }
    }

    fun toggleNodeExpansion(fileObject: FileObject) {
        val wasExpanded = expandedNodes[fileObject] == true
        expandedNodes[fileObject] = !wasExpanded

        // If we're expanding and haven't loaded yet, trigger a load
        if (!wasExpanded && !fileListCache.containsKey(fileObject)) {
            _loadingStates[fileObject] = true
        }
    }

    fun syncGitChanges(path: String): Job {
        return viewModelScope.launch {
            val gitRoot = findGitRoot(path)
            if (gitRoot != null) {
                gitViewModel.get()!!.syncChanges(File(gitRoot)).join()
                gitChanges = gitViewModel.get()!!.changes[gitRoot]!!
            }
        }
    }

    fun updateCache(file: FileObject) {
        syncGitChanges(file.getAbsolutePath())
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
                val sortedFiles = getSortedFiles(fileList)

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
                    } catch (_: Exception) {
                        _loadingStates[node.file] = false
                        return@launch
                    }

                // Process files
                val sortedFiles = getSortedFiles(fileList)

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

    private suspend fun getSortedFiles(fileObjects: List<FileObject>): List<FileTreeNode> {
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
            .map { it.toFileTreeNode() }
    }
}
