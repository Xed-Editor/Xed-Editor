package com.rk.filetree

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.file.FileObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class FileTreeSelectionManager {
    private val selectedFiles = mutableStateMapOf<FileObject, List<FileObject>>()
    private val focusedFile = mutableStateMapOf<FileObject, FileObject>()
    val expandedNodes = mutableStateMapOf<FileObject, Boolean>()
    val cutNodes = mutableStateListOf<FileObject>()
    val diagnosedNodes = mutableStateMapOf<FileObject, Int>()

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

    fun expandNode(fileObject: FileObject) {
        expandedNodes[fileObject] = true
    }

    fun toggleNodeExpansion(fileObject: FileObject): Boolean {
        val wasExpanded = expandedNodes[fileObject] == true
        expandedNodes[fileObject] = !wasExpanded
        return !wasExpanded
    }

    fun isNodeExpanded(fileObject: FileObject): Boolean = expandedNodes[fileObject] == true

    fun isFileFocused(projectFile: FileObject, fileObject: FileObject) = focusedFile[projectFile] == fileObject

    fun focusFile(scope: CoroutineScope, projectFile: FileObject, fileObject: FileObject) {
        focusedFile[projectFile] = fileObject
        scope.launch {
            delay(1000)
            focusedFile.remove(projectFile)
        }
    }

    fun expandToRoot(fileObject: FileObject, projectFile: FileObject, checkCache: (FileObject) -> Boolean) {
        var currentFile: FileObject? = fileObject
        while (currentFile != null && currentFile != projectFile) {
            expandedNodes[currentFile] = true
            if (!checkCache(currentFile)) {
                expandedNodes[currentFile] = true
            }
            currentFile = currentFile.getParentFile()
        }
        expandedNodes[projectFile] = true
    }
}
