package com.rk.ai.service

import com.google.gson.JsonObject
import java.io.File

interface EditorOps {
    fun showPatch(filePath: String, oldContent: String, newContent: String, title: String = "Review AI change", onApply: suspend () -> Unit)
    fun applyBatchEdits(edits: Map<String, String>, title: String = "Review Batch AI Changes")
    fun rejectPatch(filePath: String)
    fun openFile(file: File)
    suspend fun getOpenFiles(): List<JsonObject>
    suspend fun getActiveFile(): JsonObject?
    suspend fun getSelection(): String
    fun replaceSelection(newContent: String)
    fun insertAtCursor(newContent: String)
    suspend fun saveAllFiles(): String
    fun ensureIdeEnabled()
    fun showMessage(message: String)
}
