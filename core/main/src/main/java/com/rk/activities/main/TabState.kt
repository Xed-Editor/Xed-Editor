package com.rk.activities.main

import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import java.io.Serializable
import kotlinx.coroutines.launch

sealed interface TabState : Serializable {
    suspend fun toTab(): Tab?
}

data class EditorTabState(
    val fileObject: FileObject,
    val projectRoot: FileObject?,
    val cursor: EditorCursorState,
    val scrollX: Int,
    val scrollY: Int,
    val unsavedContent: String?,
) : TabState {
    override suspend fun toTab(): Tab? {
        if (!fileObject.exists() && !fileObject.canRead()) return null

        MainActivity.instance!!.viewModel.apply {
            val editorTab = editorManager.createEditorTab(fileObject, projectRoot)

            viewModelScope.launch {
                editorTab.editorState.contentRendered.await()
                val editor = editorTab.editorState.editor.get()!!
                unsavedContent?.let {
                    editorTab.editorState.isDirty = true
                    editor.setText(it)
                }

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

            return editorTab
        }
    }
}

data class EditorCursorState(val lineLeft: Int, val columnLeft: Int, val lineRight: Int, val columnRight: Int) :
    Serializable

data class FileTabState(val fileObject: FileObject) : TabState {
    override suspend fun toTab() = TabRegistry.getTab(fileObject, null, MainActivity.instance!!.viewModel)
}
