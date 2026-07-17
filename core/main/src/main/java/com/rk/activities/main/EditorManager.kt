package com.rk.activities.main

import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.base.TabRegistry
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialogRes
import com.rk.utils.expectOOM
import io.github.rosemoe.sora.event.SelectionChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorManager(private val viewModel: MainViewModel) {

    fun createEditorTab(
        file: FileObject?,
        projectRoot: FileObject? = null,
        isReadOnly: Boolean = false,
        customTitle: String? = null,
        fallbackExtension: String? = null,
    ): EditorTab {
        return EditorTab(
            file = file,
            projectRoot = projectRoot,
            viewModel = viewModel,
            isReadOnly = isReadOnly,
            customTitle = customTitle,
            fallbackExtension = fallbackExtension,
        )
    }

    fun addEditorTab(
        file: FileObject?,
        projectRoot: FileObject? = null,
        switchToTab: Boolean = true,
        checkDuplicate: Boolean = true,
        isReadOnly: Boolean = false,
        customTitle: String? = null,
        fallbackExtension: String? = null,
    ) {
        val editorTab = createEditorTab(file, projectRoot, isReadOnly, customTitle, fallbackExtension)
        viewModel.tabManager.addTab(editorTab, switchToTab, checkDuplicate)
    }

    suspend fun jumpToPosition(
        file: FileObject,
        projectRoot: FileObject? = null,
        lineStart: Int,
        charStart: Int,
        lineEnd: Int,
        charEnd: Int,
        isReadOnly: Boolean = false,
    ) {
        withContext(Dispatchers.Main) {
            openFile(file, projectRoot = projectRoot, switchToTab = true, isReadOnly = isReadOnly)
        }

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
        projectRoot: FileObject? = null,
        switchToTab: Boolean = true,
        checkDuplicate: Boolean = true,
        isReadOnly: Boolean = false,
        customTitle: String? = null,
    ) {
        val function = suspend {
            val tab = TabRegistry.getTab(fileObject, projectRoot, viewModel, isReadOnly, customTitle)
            withContext(Dispatchers.Main) { viewModel.tabManager.addTab(tab, switchToTab, checkDuplicate) }
        }

        if (Settings.oom_prediction && expectOOM(fileObject.length())) {
            dialogRes(
                title = strings.attention.getString(),
                msg = strings.tab_memory_warning.getString(),
                okRes = strings.continue_action,
                onOk = { viewModel.viewModelScope.launch { function.invoke() } },
            )
        } else {
            function.invoke()
        }
    }

    fun addPreviewTab(title: String, content: String, extension: String = "txt", isReadOnly: Boolean = true) {
        val editorTab =
            createEditorTab(file = null, customTitle = title, fallbackExtension = extension, isReadOnly = isReadOnly)
        viewModel.tabManager.addTab(editorTab, switchToTab = true)
        viewModel.viewModelScope.launch {
            editorTab.editorState.contentRendered
                .await() // TODO: Check if correct property and add initial content param to EditorTab() constructor
            withContext(Dispatchers.Main) {
                editorTab.editorState.editor.get()?.setText(content)
                editorTab.editorState.isDirty = false
            }
        }
    }
}
