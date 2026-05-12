package com.rk.ai.service

import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.file.FileWrapper
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.EditorPatch
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorService(
    private val viewModel: MainViewModel,
    private val notificationSender: IdeNotificationSender?
) {

    fun openFile(file: File) {
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            viewModel.editorManager.openFile(FileWrapper(file), projectRoot = null, switchToTab = true)
        }
    }

    suspend fun getOpenFiles(): List<JsonObject> {
        val current = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab }
        return viewModel.tabs.filterIsInstance<EditorTab>().map { it.toIdeFileJsonObject(it == current) }
    }

    suspend fun getActiveFile(): JsonObject? {
        val current = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab } ?: return null
        val json = current.toIdeFileJsonObject(active = true)
        val content = withContext(Dispatchers.Main) {
            current.editorState.editor.get()?.text?.toString().orEmpty()
        }
        json.addProperty("content", content)
        return json
    }

    suspend fun getSelection(): String {
        val current = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab } ?: return ""
        return withContext(Dispatchers.Main) {
            current.editorState.editor.get()?.getSelectedText().orEmpty()
        }
    }

    fun replaceSelection(newContent: String) {
        val current = viewModel.currentTab as? EditorTab ?: return
        if (Settings.ai_auto_apply) {
            viewModel.viewModelScope.launch(Dispatchers.Main) {
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
        viewModel.viewModelScope.launch(Dispatchers.Main) {
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
                    viewModel.viewModelScope.launch(Dispatchers.Main) {
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
        val current = viewModel.currentTab as? EditorTab ?: return
        if (Settings.ai_auto_apply) {
            viewModel.viewModelScope.launch(Dispatchers.Main) {
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
        viewModel.viewModelScope.launch(Dispatchers.Main) {
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
                    viewModel.viewModelScope.launch(Dispatchers.Main) {
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

    suspend fun saveAll(): String {
        val tabs = viewModel.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
        withContext(Dispatchers.Main) {
            tabs.forEach { tab ->
                tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
            }
        }
        withContext(Dispatchers.IO) { tabs.forEach { it.quickSave() } }
        return "saved ${tabs.size} dirty open file(s)"
    }

    fun showPatch(
        filePath: String, oldContent: String, newContent: String, title: String, onApply: suspend () -> Unit
    ) {
        if (Settings.ai_auto_apply) {
            viewModel.viewModelScope.launch(Dispatchers.Main) {
                onApply()
                notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                    addProperty("filePath", filePath)
                })
            }
            return
        }
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            var tab = findTabByPath(filePath)
            if (tab == null) {
                viewModel.editorManager.openFile(FileWrapper(File(filePath)), projectRoot = null, switchToTab = true)
                var attempts = 0
                while (tab == null && attempts < 20) {
                    delay(50)
                    tab = findTabByPath(filePath)
                    attempts++
                }
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
                    viewModel.viewModelScope.launch(Dispatchers.Main) {
                        runCatching { onApply() }
                            .onSuccess {
                                viewModel.showAiSheet = true
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
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            findTabByPath(filePath)?.let {
                it.editorState.pendingAiPatch?.reject?.invoke()
                it.editorState.pendingAiPatch = null
            }
        }
    }

    fun showMessage(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) toast(message)
        else viewModel.viewModelScope.launch(Dispatchers.Main) { toast(message) }
    }

    fun ensureIdeEnabled() {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
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

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return viewModel.tabs.filterIsInstance<EditorTab>().find {
            val tabCanonical = runCatching { File(it.file.getAbsolutePath()).canonicalPath }
                .getOrDefault(File(it.file.getAbsolutePath()).absolutePath)
            tabCanonical == canonical
        }
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
