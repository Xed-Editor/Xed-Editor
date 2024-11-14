package com.rk.xededitor.MainActivity.file.filetree.events

import java.io.File

class FileTreeEvents {
    data class OnCreateFileEvent(val file: File, val openedFolder: File)
    data class OnCreateFolderEvent(val file: File, val openedFolder: File)
    data class OnRefreshFolderEvent(val openedFolder: File)
    data class OnRenameFileEvent(val oldFile: File, val newFile: File, val openedFolder: File)
    data class OnDeleteFileEvent(val file: File, val openedFolder: File)
}