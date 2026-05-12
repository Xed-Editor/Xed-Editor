package com.rk.ai.service

import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.lsp.applyFormattingOptions
import com.rk.tabs.editor.EditorTab
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.document.applyEdits
import io.github.rosemoe.sora.lsp.events.format.fullFormatting
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LspService(private val viewModel: MainViewModel) {

    suspend fun getDiagnostics(filePath: String): JsonArray {
        val results = JsonArray()
        val tab = findTabByPath(filePath) ?: return results
        withContext(Dispatchers.Main) {
            tab.editorState.diagnostics.forEach { diag ->
                results.add(JsonObject().apply {
                    val messageStr = if (diag.message.isLeft) diag.message.left else diag.message.right.toString()
                    addProperty("message", messageStr)
                    addProperty("severity", diag.severity.name)
                    add("range", JsonObject().apply {
                        add("start", JsonObject().apply { addProperty("line", diag.range.start.line + 1); addProperty("character", diag.range.start.character + 1) })
                        add("end", JsonObject().apply { addProperty("line", diag.range.end.line + 1); addProperty("character", diag.range.end.character + 1) })
                    })
                    diag.code?.let { addProperty("code", if (it.isLeft) it.left else it.right.toString()) }
                    diag.source?.let { addProperty("source", it) }
                })
            }
        }
        return results
    }

    suspend fun findDefinitions(filePath: String, line: Int, column: Int): JsonArray {
        val results = JsonArray()
        val tab = findTabByPath(filePath) ?: return results
        val connector = tab.lspConnector ?: return results
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return results
        withContext(Dispatchers.IO) {
            runCatching {
                withContext(Dispatchers.Main) { editor.cursor.set(line - 1, column - 1) }
                val either = connector.requestDefinition(editor)
                val locations = if (either.isLeft) either.left else either.right.map {
                    org.eclipse.lsp4j.Location(it.targetUri, it.targetSelectionRange)
                }
                locations.forEach { loc ->
                    results.add(JsonObject().apply {
                        addProperty("uri", loc.uri)
                        add("range", JsonObject().apply {
                            add("start", JsonObject().apply { addProperty("line", loc.range.start.line + 1); addProperty("character", loc.range.start.character + 1) })
                            add("end", JsonObject().apply { addProperty("line", loc.range.end.line + 1); addProperty("character", loc.range.end.character + 1) })
                        })
                    })
                }
            }
        }
        return results
    }

    suspend fun findReferences(filePath: String, line: Int, column: Int): JsonArray {
        val results = JsonArray()
        val tab = findTabByPath(filePath) ?: return results
        val connector = tab.lspConnector ?: return results
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return results
        withContext(Dispatchers.IO) {
            runCatching {
                withContext(Dispatchers.Main) { editor.cursor.set(line - 1, column - 1) }
                connector.requestReferences(editor).forEach { loc ->
                    loc?.let { l ->
                        results.add(JsonObject().apply {
                            addProperty("uri", l.uri)
                            add("range", JsonObject().apply {
                                add("start", JsonObject().apply { addProperty("line", l.range.start.line + 1); addProperty("character", l.range.start.character + 1) })
                                add("end", JsonObject().apply { addProperty("line", l.range.end.line + 1); addProperty("character", l.range.end.character + 1) })
                            })
                        })
                    }
                }
            }
        }
        return results
    }

    fun renameSymbol(filePath: String, line: Int, column: Int, newName: String) {
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            val tab = findTabByPath(filePath) ?: return@launch
            val connector = tab.lspConnector ?: return@launch
            val editor = tab.editorState.editor.get() ?: return@launch
            withContext(Dispatchers.IO) {
                runCatching {
                    withContext(Dispatchers.Main) { editor.cursor.set(line - 1, column - 1) }
                    val workspaceEdit = connector.requestRenameSymbol(editor, newName)
                    val edits = workspaceEdit.changes[tab.file.toUri().toString()]
                    if (edits != null) {
                        withContext(Dispatchers.Main) {
                            connector.getEventManager()!!.emitAsync(EventType.applyEdits) {
                                put("edits", edits); put(editor.text)
                            }
                            com.rk.utils.toast("Symbol renamed (local file only)")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            com.rk.utils.toast("Rename symbol not found in current file or multiple files affected.")
                        }
                    }
                }
            }
        }
    }

    suspend fun formatDocument(filePath: String) {
        val tab = findTabByPath(filePath) ?: return
        val connector = tab.lspConnector ?: return
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return
        val eventManager = connector.getEventManager() ?: return
        withContext(Dispatchers.Main) {
            applyFormattingOptions(eventManager, tab)
            eventManager.emitAsync(EventType.fullFormatting, editor.text)
        }
    }

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return viewModel.tabs.filterIsInstance<EditorTab>().find {
            val tabCanonical = runCatching { File(it.file.getAbsolutePath()).canonicalPath }
                .getOrDefault(File(it.file.getAbsolutePath()).absolutePath)
            tabCanonical == canonical
        }
    }
}
