package com.rk.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color

object FileChangeNotifier {
    val fileChangeListeners = mutableStateListOf<suspend (String) -> Unit>()
    val projectOpenListeners = mutableStateListOf<suspend (String) -> Unit>()

    suspend fun notifyFileChanged(path: String) {
        fileChangeListeners.forEach { it(path) }
    }

    suspend fun notifyProjectOpened(root: String) {
        projectOpenListeners.forEach { it(root) }
    }
}

data class FileDecoration(
    val color: Color? = null,
    val badge: String? = null
)

interface FileDecorationProvider {
    @Composable
    fun getDecoration(file: FileObject): FileDecoration?
}

object FileDecorationRegistry {
    var provider: FileDecorationProvider? = null
}

data class FileProperty(
    val label: String,
    val value: String,
    val valueColor: Color? = null
)

interface FilePropertiesProvider {
    @Composable
    fun getProperties(file: FileObject): List<FileProperty>
}

object FilePropertiesRegistry {
    val providers = mutableStateListOf<FilePropertiesProvider>()
}
