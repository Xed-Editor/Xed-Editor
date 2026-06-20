package com.rk.tabs.base

import com.rk.activities.main.MainViewModel
import com.rk.file.BuiltinFileType
import com.rk.file.FileObject
import com.rk.file.FileTypeManager
import com.rk.tabs.image.ImageTab
import java.util.concurrent.ConcurrentHashMap

fun interface TabFactory {
    fun createTab(file: FileObject, projectRoot: FileObject?, viewModel: MainViewModel): Tab
}

object TabRegistry {
    private val registeredTabs = ConcurrentHashMap<String, TabFactory>()

    fun registerTab(tabFactory: TabFactory, fileExtensions: List<String>) {
        fileExtensions.forEach { registeredTabs[it] = tabFactory }
    }

    fun unregisterTab(tabFactory: TabFactory) {
        registeredTabs.values.remove(tabFactory)
    }

    fun getTab(file: FileObject, projectRoot: FileObject?, viewModel: MainViewModel): Tab {
        val ext = file.getExtension()
        val type = FileTypeManager.fromExtension(ext)

        val factory = registeredTabs[ext]
        if (factory != null) {
            return factory.createTab(file, projectRoot, viewModel)
        }

        return when (type) {
            BuiltinFileType.IMAGE -> ImageTab(file)
            else -> viewModel.editorManager.createEditorTab(file, projectRoot)
        }
    }
}
