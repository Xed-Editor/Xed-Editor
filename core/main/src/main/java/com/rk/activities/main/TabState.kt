package com.rk.activities.main

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import com.rk.file.toFileObject
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import kotlinx.coroutines.launch

sealed interface TabState {
    suspend fun toTab(): Tab?
}

data class EditorTabState(
    val fileUri: String,
    val projectRootUri: String?,
    val cursor: EditorCursorState,
    val scrollX: Int,
    val scrollY: Int,
    val unsavedContent: String?,
    val encoding: String? = null,
) : TabState {
    override suspend fun toTab(): Tab? {
        val fileObject = Uri.parse(fileUri).toFileObject(true)
        val projectRoot = projectRootUri?.let { Uri.parse(it).toFileObject(false) }

        if (!fileObject.exists() || !fileObject.canRead()) return null

        val activity = MainActivity.instance ?: return null
        activity.viewModel.apply {
            val editorTab = editorManager.createEditorTab(fileObject, projectRoot, encoding)

            val contentReady = kotlinx.coroutines.CompletableDeferred<Unit>()
            viewModelScope.launch {
                editorTab.editorState.contentRendered.await()
                val editor = editorTab.editorState.editor.get() ?: run {
                    contentReady.complete(Unit)
                    return@launch
                }
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
                contentReady.complete(Unit)
            }

            contentReady.await()
            return editorTab
        }
    }
}

data class EditorCursorState(val lineLeft: Int, val columnLeft: Int, val lineRight: Int, val columnRight: Int)

data class FileTabState(val fileUri: String) : TabState {
    override suspend fun toTab(): Tab? {
        val fileObject = Uri.parse(fileUri).toFileObject(true)
        val activity = MainActivity.instance ?: return null
        return TabRegistry.getTab(fileObject, null, activity.viewModel)
    }
}
