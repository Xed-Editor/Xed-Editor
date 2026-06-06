package com.rk.ai.service

import android.os.Looper
import androidx.compose.runtime.snapshotFlow
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.service.IdeNotificationSender
import com.rk.file.FileWrapper
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.EditorPatch
import com.rk.utils.toast
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class EditorService(
    private val tabRepo: TabRepository,
    private val scope: ScopeProvider,
    private val fileOpener: FileOpener,
    private val notificationSender: IdeNotificationSender?
) {

    fun openFile(file: File) {
        fileOpener.openFileInEditor(file)
    }

    suspend fun getOpenFiles(): List<JsonObject> {
        val current = withContext(Dispatchers.Main) { tabRepo.currentTab as? EditorTab }
        return tabRepo.tabs.filterIsInstance<EditorTab>().map { it.toIdeFileJsonObject(it == current) }
    }

    suspend fun getActiveFile(): JsonObject? {
        val current = withContext(Dispatchers.Main) { tabRepo.currentTab as? EditorTab } ?: return null
        val json = current.toIdeFileJsonObject(active = true)
        val content = withContext(Dispatchers.Main) {
            current.editorState.editor.get()?.text?.toString().orEmpty()
        }
        json.addProperty("content", if (content.length > 512_000) content.take(512_000) + "\n\n... (truncated at 500KB)" else content)
        return json
    }

    suspend fun getSelection(): String {
        val current = withContext(Dispatchers.Main) { tabRepo.currentTab as? EditorTab } ?: return ""
        return withContext(Dispatchers.Main) {
            current.editorState.editor.get()?.getSelectedText().orEmpty()
        }
    }

    fun replaceSelection(newContent: String) {
        val current = tabRepo.currentTab as? EditorTab ?: return
        if (Settings.ai_auto_apply) {
            scope.viewModelScope.launch(Dispatchers.Main) {
                val editor = current.editorState.editor.get() ?: return@launch
                val hasSelection = editor.isTextSelected
                val start = if (hasSelection) minOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex) else 0
                val end = if (hasSelection) maxOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex) else editor.text.toString().length
                editor.text.replace(start, end, newContent)
                current.editorState.isDirty = true
                notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                    addProperty("filePath", current.file.getAbsolutePath())
                })
            }
            return
        }
        scope.viewModelScope.launch(Dispatchers.Main) {
            val editor = current.editorState.editor.get() ?: return@launch
            val hasSelection = editor.isTextSelected
            val start = if (hasSelection) minOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex) else 0
            val end = if (hasSelection) maxOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex) else editor.text.toString().length
            val oldText = editor.text.substring(start, end)
            current.editorState.pendingAiPatch?.reject?.invoke()
            current.editorState.pendingAiPatch = EditorPatch(
                title = if (hasSelection) "Review Gemini selection replacement" else "Review Gemini file replacement",
                filePath = current.file.getAbsolutePath(),
                oldText = oldText,
                newText = newContent,
                apply = {
                    scope.viewModelScope.launch(Dispatchers.Main) {
                        editor.text.replace(start, end, newContent)
                        current.editorState.isDirty = true
                        notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                            addProperty("filePath", current.file.getAbsolutePath())
                        })
                    }
                },
                reject = {
                    notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply {
                        addProperty("filePath", current.file.getAbsolutePath())
                    })
                }
            )
        }
    }

    fun insertAtCursor(newContent: String) {
        val current = tabRepo.currentTab as? EditorTab ?: return
        if (Settings.ai_auto_apply) {
            scope.viewModelScope.launch(Dispatchers.Main) {
                val editor = current.editorState.editor.get() ?: return@launch
                val line = editor.cursor.leftLine
                val column = editor.cursor.leftColumn
                editor.text.insert(line, column, newContent)
                current.editorState.isDirty = true
                notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                    addProperty("filePath", current.file.getAbsolutePath())
                })
            }
            return
        }
        scope.viewModelScope.launch(Dispatchers.Main) {
            val editor = current.editorState.editor.get() ?: return@launch
            val line = editor.cursor.leftLine
            val column = editor.cursor.leftColumn
            current.editorState.pendingAiPatch?.reject?.invoke()
            current.editorState.pendingAiPatch = EditorPatch(
                title = "Review AI insertion",
                filePath = current.file.getAbsolutePath(),
                oldText = "",
                newText = newContent,
                apply = {
                    scope.viewModelScope.launch(Dispatchers.Main) {
                        editor.text.insert(line, column, newContent)
                        current.editorState.isDirty = true
                        notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                            addProperty("filePath", current.file.getAbsolutePath())
                        })
                    }
                },
                reject = {
                    notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply {
                        addProperty("filePath", current.file.getAbsolutePath())
                    })
                }
            )
        }
    }

    suspend fun saveAllFiles(): String {
        val dirtyTabs = tabRepo.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
        withContext(Dispatchers.Main) {
            dirtyTabs.forEach { tab ->
                tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
            }
        }
        withContext(Dispatchers.IO) { dirtyTabs.forEach { it.quickSave() } }
        return "saved ${dirtyTabs.size} dirty open file(s)"
    }

    fun applyBatchEdits(edits: Map<String, String>, title: String) {
        scope.viewModelScope.launch(Dispatchers.IO) {
            edits.forEach { (path, newContent) ->
                val file = File(path)
                val oldContent = getFileContentInternal(path)
                withContext(Dispatchers.Main) {
                    showPatch(path, oldContent, newContent, title) {
                        writeFile(file, newContent)
                    }
                }
            }
        }
    }

    private suspend fun getFileContentInternal(path: String): String {
        val file = File(path)
        return findTabByPath(path)?.let { tab ->
            withContext(Dispatchers.Main) { tab.editorState.editor.get()?.text?.toString().orEmpty() }
        } ?: withContext(Dispatchers.IO) { if (file.exists()) file.readText() else "" }
    }

    private suspend fun writeFile(file: File, content: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
        withContext(Dispatchers.Main) { findTabByPath(file.absolutePath)?.refresh() }
    }

    fun showPatch(
        filePath: String, oldContent: String, newContent: String, title: String, onApply: suspend () -> Unit
    ) {
        if (Settings.ai_auto_apply) {
            scope.viewModelScope.launch(Dispatchers.Main) {
                onApply()
                notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                    addProperty("filePath", filePath)
                })
            }
            return
        }
        scope.viewModelScope.launch(Dispatchers.Main) {
            var tab = findTabByPath(filePath)
            if (tab == null) {
                fileOpener.openFileInEditor(File(filePath))
                tab = runCatching {
                    withTimeout(2000) {
                        snapshotFlow { tabRepo.tabs }
                            .map { ts -> ts.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath } }
                            .filterNotNull()
                            .first()
                    }
                }.getOrNull()
            }
            if (tab == null) {
                notificationSender?.sendNotification("ide/error", JsonObject().apply {
                    addProperty("message", "Failed to open file for patching: $filePath")
                })
                return@launch
            }
            tab.editorState.pendingAiPatch?.reject?.invoke()
            tab.editorState.pendingAiPatch = EditorPatch(
                title = title, filePath = filePath, oldText = oldContent, newText = newContent,
                apply = {
                    scope.viewModelScope.launch(Dispatchers.Main) {
                        runCatching { onApply() }
                            .onSuccess {
                                notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                                    addProperty("filePath", filePath)
                                })
                            }
                            .onFailure {
                                toast("AI apply failed: ${it.message ?: it::class.java.simpleName}")
                                notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply {
                                    addProperty("filePath", filePath)
                                    addProperty("reason", it.message ?: "apply failed")
                                })
                            }
                    }
                },
                reject = {
                    notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply {
                        addProperty("filePath", filePath)
                    })
                }
            )
        }
    }

    fun rejectPatch(filePath: String) {
        scope.viewModelScope.launch(Dispatchers.Main) {
            findTabByPath(filePath)?.let {
                it.editorState.pendingAiPatch?.reject?.invoke()
                it.editorState.pendingAiPatch = null
            }
        }
    }

    fun showMessage(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) toast(message)
        else scope.viewModelScope.launch(Dispatchers.Main) { toast(message) }
    }

    fun ensureIdeEnabled() {
        scope.viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val home = com.rk.file.sandboxHomeDir()
                val geminiDir = File(home, ".gemini").also { it.mkdirs() }
                val settingsFile = File(geminiDir, "settings.json")
                val settings = if (settingsFile.exists()) {
                    runCatching { com.google.gson.JsonParser.parseString(settingsFile.readText()).asJsonObject }.getOrDefault(JsonObject())
                } else JsonObject()
                settings.getAsJsonObject("general")?.apply { addProperty("preferredEditor", "vim") }
                    ?: settings.add("general", JsonObject().apply { addProperty("preferredEditor", "vim") })
                settings.getAsJsonObject("ide")?.apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) }
                    ?: settings.add("ide", JsonObject().apply { addProperty("enabled", true); addProperty("hasSeenNudge", true) })
                settings.getAsJsonObject("privacy")?.apply { addProperty("usageStatisticsEnabled", false) }
                    ?: settings.add("privacy", JsonObject().apply { addProperty("usageStatisticsEnabled", false) })
                settings.getAsJsonObject("telemetry")?.apply { addProperty("enabled", false) }
                    ?: settings.add("telemetry", JsonObject().apply { addProperty("enabled", false) })
                settingsFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(settings))
            }
        }
    }

    private val editorTabCache = ConcurrentHashMap<String, EditorTab>()

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        editorTabCache[canonical]?.let {
            if (it in tabRepo.tabs) return it
        }
        val tab = tabRepo.tabs.filterIsInstance<EditorTab>().find {
            val tabCanonical = runCatching { File(it.file.getAbsolutePath()).canonicalPath }
                .getOrDefault(File(it.file.getAbsolutePath()).absolutePath)
            tabCanonical == canonical
        }
        if (tab != null) {
            editorTabCache[canonical] = tab
            if (editorTabCache.size > 64) {
                editorTabCache.keys.firstOrNull()?.let { editorTabCache.remove(it) }
            }
        }
        return tab
    }

    private suspend fun EditorTab.toIdeFileJsonObject(active: Boolean): JsonObject {
        val editor = withContext(Dispatchers.Main) { editorState.editor.get() }
        val selected = editor?.getSelectedText().orEmpty()
        val line = editor?.cursor?.leftLine?.plus(1) ?: 1
        val column = editor?.cursor?.leftColumn?.plus(1) ?: 1
        return JsonObject().apply {
            addProperty("path", file.getAbsolutePath())
            addProperty("timestamp", System.currentTimeMillis())
            addProperty("isActive", active)
            add("cursor", JsonObject().apply {
                addProperty("line", line)
                addProperty("character", column)
            })
            addProperty("selectedText", selected.take(16 * 1024))
        }
    }
}
