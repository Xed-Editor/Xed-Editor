package com.rk.filetree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.file.FileObject

class FileTreeDialogState {
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

    var showCreateDialog by mutableStateOf(false)
        private set
    var isCreateFile by mutableStateOf(true)
        private set
    var createValue by mutableStateOf("")
    var createError by mutableStateOf<String?>(null)
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
}
