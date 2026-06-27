package com.rk.filetree

import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject

object FileActionProvider {
    private val _fileActions =
        mutableListOf(
            CloseAction,
            RefreshAction,
            CreateNewFileAction,
            CreateNewFolderAction,
            RenameAction,
            DeleteAction,
            CopyAction,
            CutAction,
            PasteAction,
            OpenWithAction,
            SaveAsAction,
            // AddFileAction,
            OpenAsProjectAction,
            PropertiesAction,
        )

    val fileActions: List<BaseFileAction>
        get() = _fileActions.toList()

    @XedExtensionPoint
    fun registerAction(action: BaseFileAction) {
        if (!_fileActions.contains(action)) {
            _fileActions.add(action)
        }
    }

    @XedExtensionPoint
    fun unregisterAction(action: BaseFileAction) {
        _fileActions.remove(action)
    }

    fun getActions(file: FileObject, root: FileObject?) = getActions(listOf(file), root)

    fun getActions(files: List<FileObject>, root: FileObject?): List<BaseFileAction> {
        if (files.isEmpty()) return emptyList()

        val suitableActions = mutableListOf<BaseFileAction>()

        fileActions.forEach { action ->
            val isBulkAction = files.size > 1

            if (action is MultiFileAction) {
                val isSupported = action.isSupported(files)
                if (!isSupported) return@forEach

                val hasFolders = files.any { it.isDirectory() }
                val hasFiles = files.any { it.isFile() }
                val hasRoot = files.any { it == root }

                val folderValid = if (!hasFolders) true else action.type.folder
                val fileValid = if (!hasFiles) true else action.type.file
                val rootValid = if (!hasRoot) true else action.type.rootFolder

                if (folderValid && fileValid && rootValid) {
                    suitableActions.add(action)
                }
            } else if (!isBulkAction) {
                val file = files.first()
                val action = action as FileAction

                val isSupported = action.isSupported(file)
                if (!isSupported) return@forEach

                val isRootAction = file == root && action.type.rootFolder
                val isFileAction = file.isFile() && action.type.file
                val isFolderAction = file.isDirectory() && action.type.folder

                if (isRootAction || isFileAction || isFolderAction) {
                    suitableActions.add(action)
                }
            }
        }

        return suitableActions
    }
}
