package com.rk.file

import androidx.compose.runtime.mutableStateListOf

enum class FileStatus {
    ADDED,
    UNTRACKED,
    DELETED,
    CONFLICTING,
    MODIFIED,
    RENAMED,
}

interface FileStatusProvider {
    fun getStatus(path: String): FileStatus?
}

object FileStatusRegistry {
    var provider: FileStatusProvider? = null
}

object FileChangeNotifier {
    val fileChangeListeners = mutableStateListOf<(String) -> Unit>()
    val repositoryOpenListeners = mutableStateListOf<(String) -> Unit>()

    fun notifyFileChanged(path: String) {
        fileChangeListeners.forEach { it(path) }
    }

    fun notifyRepositoryOpened(root: String) {
        repositoryOpenListeners.forEach { it(root) }
    }
}
