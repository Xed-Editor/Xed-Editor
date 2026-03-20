package com.rk.filetree

import com.rk.file.FileObject

val fileActions =
    listOf(
        CloseAction,
        RefreshAction,
        TerminalAction,
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
