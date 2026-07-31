package com.rk.activities.main.session

import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainActivity
import com.rk.file.FileObject
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import kotlinx.coroutines.launch
import java.io.Serializable

sealed interface TabState : Serializable {
    suspend fun toTab(): Tab?
}

data class EditorTabState(
    val fileObject: FileObject?,
    val projectRoot: FileObject?,
    val content: String?,
    val isDirty: Boolean = false,
    val isReadOnly: Boolean = false,
    val customTitle: String? = null,
    val fallbackExtension: String = "txt",
) : TabState {
    override suspend fun toTab(): Tab? {
        if (fileObject != null && !fileObject.exists() && !fileObject.canRead()) return null

        return MainActivity.instance?.viewModel?.run {
            val editorTab =
                editorManager.createEditorTab(
                    file = fileObject,
                    projectRoot = projectRoot,
                    isReadOnly = isReadOnly,
                    customTitle = customTitle,
                    fallbackExtension = fallbackExtension,
                    initialContent = content,
                )

            viewModelScope.launch {
                editorTab.editorState.contentRendered.await()
                editorTab.editorState.isDirty = isDirty
            }

            editorTab
        }
    }
}


data class FileTabState(val fileObject: FileObject) : TabState {
    override suspend fun toTab() = TabRegistry.getTab(fileObject, null, MainActivity.instance!!.viewModel, false, null)
}
