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
    val cursor: EditorCursorState,
    val scrollX: Int,
    val scrollY: Int,
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
                val editor = editorTab.editorState.editor.get()!!

                editorTab.editorState.isDirty = isDirty

                val maxLine = editor.text.lineCount - 1
                val lineLeft = cursor.lineLeft.coerceAtMost(maxLine)
                val lineRight = cursor.lineRight.coerceAtMost(maxLine)

                val maxColumnLeft = editor.text.getColumnCount(lineLeft)
                val maxColumnRight = editor.text.getColumnCount(lineRight)
                val columnLeft = cursor.columnLeft.coerceAtMost(maxColumnLeft)
                val columnRight = cursor.columnRight.coerceAtMost(maxColumnRight)

                editor.setSelectionRegion(lineLeft, columnLeft, lineRight, columnRight)
                editor.scroller.startScroll(scrollX, scrollY, 0, 0)
            }

            editorTab
        }
    }
}

data class EditorCursorState(val lineLeft: Int, val columnLeft: Int, val lineRight: Int, val columnRight: Int) :
    Serializable

data class FileTabState(val fileObject: FileObject) : TabState {
    override suspend fun toTab() = TabRegistry.getTab(fileObject, null, MainActivity.instance!!.viewModel, false, null)
}
