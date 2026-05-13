package com.rk.activities.main

import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.base.TabRegistry
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialog
import com.rk.utils.expectOOM
import io.github.rosemoe.sora.event.SelectionChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorManager(private val viewModel: MainViewModel) {

    fun createEditorTab(file: FileObject, projectRoot: FileObject?): EditorTab {
        return EditorTab(file = file, projectRoot = projectRoot, viewModel = viewModel)
    }

    fun addEditorTab(file: FileObject, projectRoot: FileObject?, switchToTab: Boolean, checkDuplicate: Boolean = true) {
        val editorTab = createEditorTab(file, projectRoot)
        viewModel.tabManager.addTab(editorTab, switchToTab, checkDuplicate)
    }

    suspend fun jumpToPosition(
        file: FileObject,
        projectRoot: FileObject?,
        lineStart: Int,
        charStart: Int,
        lineEnd: Int,
        charEnd: Int,
    ) {
        withContext(Dispatchers.Main) { openFile(file, projectRoot = projectRoot, switchToTab = true) }

        val targetTab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file == file } ?: return

        // Wait until editor content is loaded
        targetTab.editorState.contentRendered.await()

        withContext(Dispatchers.Main) {
            targetTab.editorState.editor
                .get()
                ?.setSelectionRegion(lineStart, charStart, lineEnd, charEnd, SelectionChangeEvent.CAUSE_SEARCH)

            targetTab.editorState.editor.get()?.ensureSelectionVisible()
        }
    }

    suspend fun openFile(
        fileObject: FileObject,
        projectRoot: FileObject?,
        switchToTab: Boolean,
        checkDuplicate: Boolean = true,
    ) {
        val function = suspend {
            val tab = TabRegistry.getTab(fileObject, projectRoot, viewModel)
            withContext(Dispatchers.Main) { viewModel.tabManager.addTab(tab, switchToTab, checkDuplicate) }
        }

        if (Settings.oom_prediction && expectOOM(fileObject.length())) {
            dialog(
                title = strings.attention.getString(),
                msg = strings.tab_memory_warning.getString(),
                okString = strings.continue_action,
                onOk = { viewModel.viewModelScope.launch { function.invoke() } },
            )
        } else {
            function.invoke()
        }
    }
}
