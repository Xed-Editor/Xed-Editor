package com.rk.activities.main

import com.rk.file.FileObject
import java.io.Serializable

sealed interface TabState : Serializable

data class EditorTabState(
    val fileObject: FileObject,
    val cursor: EditorCursorState,
    val scrollX: Int,
    val scrollY: Int,
    val unsavedContent: String?,
) : TabState

data class EditorCursorState(val lineLeft: Int, val columnLeft: Int, val lineRight: Int, val columnRight: Int) :
    Serializable

data class FileTabState(val fileObject: FileObject) : TabState
