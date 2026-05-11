package com.rk.ai.service

import android.os.Looper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.GeminiBridge
import com.rk.ai.geminiDisplayRootFor
import com.rk.ai.geminiIdeWorkspacePath
import com.rk.ai.geminiResolveWorkspacePath
import com.rk.exec.ShellUtils
import com.rk.file.FileWrapper
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.GeminiEditorPatch
import com.rk.utils.toast
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope

interface GeminiNotificationSender {
    fun sendNotification(method: String, params: JsonObject)
}

class GeminiIdeServiceImpl(
    private val viewModel: MainViewModel,
    private val notificationSender: GeminiNotificationSender? = null
) : GeminiIdeService {

    override fun resolvePath(path: String): File? {
        val normalized = path.trim()
        if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized)?.let { return it }
        }
        return geminiResolveWorkspacePath(GeminiBridge.workspacePathForResolution(), path)
    }

    private fun resolveRelativePathFromOpenEditor(path: String): File? {
        val activeTab = viewModel.currentTab as? EditorTab
        val activeBase = activeTab?.let { File(it.file.getAbsolutePath()).parentFile }
        activeBase?.let { parent ->
            geminiResolveWorkspacePath(GeminiBridge.workspacePathForResolution(), File(parent, path).path)?.let { return it }
        }

        val exactMatches = viewModel.tabs
            .filterIsInstance<EditorTab>()
            .mapNotNull { tab ->
                val tabFile = File(tab.file.getAbsolutePath())
                if (tabFile.path.endsWith(File.separator + path)) {
                    geminiResolveWorkspacePath(GeminiBridge.workspacePathForResolution(), tabFile.path)
                } else {
                    null
                }
            }
            .distinctBy { it.absolutePath }
        if (exactMatches.size == 1) {
            return exactMatches.first()
        }

        return null
    }

    override fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules")
        val root = geminiDisplayRootFor(GeminiBridge.workspacePathForResolution(), directory)
        val output = mutableListOf<String>()

        fun visit(current: File) {
            if (output.size >= maxFiles) return
            current.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.forEach { child ->
                    if (output.size >= maxFiles) return
                    if (child.name in ignored) return@forEach
                    val relative = child.relativeToOrSelf(root).path + if (child.isDirectory) "/" else ""
                    output.add(relative)
                    if (recursive && child.isDirectory) visit(child)
                }
        }

        visit(directory)
        return output
    }

    override suspend fun getFileContent(filePath: String): String? {
        val openTab = findTabByPath(filePath)
        return if (openTab != null) {
            withContext(Dispatchers.Main) {
                openTab.editorState.editor.get()?.text?.toString()
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching { File(filePath).readText() }.getOrNull()
            }
        }
    }

    override fun showPatch(
        filePath: String,
        oldContent: String,
        newContent: String,
        title: String,
        onApply: suspend () -> Unit
    ) {
        if (Settings.gemini_auto_apply) {
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
                tab = findTabByPath(filePath)
            }

            if (tab == null) return@launch

            // Reject existing patch if any
            tab.editorState.pendingGeminiPatch?.reject?.invoke()

            tab.editorState.pendingGeminiPatch =
                GeminiEditorPatch(
                    title = title,
                    filePath = filePath,
                    oldText = oldContent,
                    newText = newContent,
                    apply = {
                        viewModel.viewModelScope.launch(Dispatchers.Main) {
                            runCatching { onApply() }
                                .onSuccess {
                                    viewModel.showGeminiSheet = true
                                    notificationSender?.sendNotification("ide/diffAccepted", JsonObject().apply {
                                        addProperty("filePath", filePath)
                                    })
                                }
                                .onFailure {
                                    toast("Gemini apply failed: ${it.message ?: it::class.java.simpleName}")
                                    notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply {
                                        addProperty("filePath", filePath)
                                        addProperty("reason", it.message ?: "apply failed")
                                    })
                                }
                        }
                    },
                    reject = {
                        notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply { addProperty("filePath", filePath) })
                    },
                )
        }
    }

    override fun rejectPatch(filePath: String) {
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            val tab = findTabByPath(filePath)
            tab?.let {
                it.editorState.pendingGeminiPatch?.reject?.invoke()
                it.editorState.pendingGeminiPatch = null
            }
        }
    }

    override suspend fun writeFile(file: File, content: String) {
        val tab = findTabByPath(file.absolutePath)
        withContext(Dispatchers.IO) {
            tab?.saveMutex?.withLock {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            } ?: run {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }
        }

        withContext(Dispatchers.Main) {
            tab?.let {
                it.editorState.editor.get()?.setText(content)
                it.editorState.content = it.editorState.editor.get()?.text
                it.editorState.updateUndoRedo()
                it.editorState.isDirty = false
            }
        }
    }

    override fun refreshEditors(filePath: String?, force: Boolean) {
        if (filePath != null) {
            val requested = runCatching { File(filePath).canonicalPath }.getOrDefault(File(filePath).absolutePath)
            val tab = viewModel.tabs
                .filterIsInstance<EditorTab>()
                .find { tab ->
                    val tabPath = runCatching { File(tab.file.getAbsolutePath()).canonicalPath }
                        .getOrDefault(File(tab.file.getAbsolutePath()).absolutePath)
                    tabPath == requested
                } ?: return
            if (!force && tab.editorState.isDirty) return
            tab.refresh()
        } else {
            viewModel.tabs.filterIsInstance<EditorTab>().forEach {
                if (force || !it.editorState.isDirty) it.refresh()
            }
        }
    }

    override fun openFile(file: File) {
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            viewModel.editorManager.openFile(FileWrapper(file), projectRoot = null, switchToTab = true)
        }
    }

    override suspend fun getOpenFiles(): List<JsonObject> {
        val current = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab }
        return viewModel.tabs.filterIsInstance<EditorTab>().map { it.toIdeFileJsonObject(it == current) }
    }

    override suspend fun getActiveFile(): JsonObject? {
        val current = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab } ?: return null
        val json = current.toIdeFileJsonObject(active = true)
        val content = withContext(Dispatchers.Main) {
            current.editorState.editor.get()?.text?.toString().orEmpty()
        }
        json.addProperty("content", content)
        return json
    }

    override suspend fun getSelection(): String {
        val current = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab } ?: return ""
        return withContext(Dispatchers.Main) {
            current.editorState.editor.get()?.getSelectedText().orEmpty()
        }
    }

    override fun replaceSelection(newContent: String) {
        val current = viewModel.currentTab as? EditorTab ?: return
        
        if (Settings.gemini_auto_apply) {
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

            // Reject existing patch if any
            current.editorState.pendingGeminiPatch?.reject?.invoke()

            current.editorState.pendingGeminiPatch =
                GeminiEditorPatch(
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

    override fun insertAtCursor(newContent: String) {
        val current = viewModel.currentTab as? EditorTab ?: return
        
        if (Settings.gemini_auto_apply) {
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

            // Reject existing patch if any
            current.editorState.pendingGeminiPatch?.reject?.invoke()

            current.editorState.pendingGeminiPatch =
                GeminiEditorPatch(
                    title = "Review Gemini insertion",
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

    override suspend fun saveAll(): String {
        val tabs = viewModel.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
        withContext(Dispatchers.Main) {
            tabs.forEach { tab ->
                tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
            }
        }
        // Run saves in parallel on IO dispatcher to avoid blocking the main thread sequentially
        withContext(Dispatchers.IO) {
            tabs.forEach { tab -> tab.quickSave() }
        }
        return "saved ${tabs.size} dirty open file(s)"
    }

    override fun ensureIdeEnabled() {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val home = com.rk.file.sandboxHomeDir()
                val geminiDir = File(home, ".gemini")
                geminiDir.mkdirs()
                val settingsFile = File(geminiDir, "settings.json")
                
                val settings = if (settingsFile.exists()) {
                    runCatching { com.google.gson.JsonParser.parseString(settingsFile.readText()).asJsonObject }.getOrDefault(JsonObject())
                } else {
                    JsonObject()
                }

                val general = settings.getAsJsonObject("general") ?: JsonObject()
                general.addProperty("preferredEditor", "vim")
                settings.add("general", general)

                val ide = settings.getAsJsonObject("ide") ?: JsonObject()
                ide.addProperty("enabled", true)
                ide.addProperty("hasSeenNudge", true)
                settings.add("ide", ide)

                val privacy = settings.getAsJsonObject("privacy") ?: JsonObject()
                privacy.addProperty("usageStatisticsEnabled", false)
                settings.add("privacy", privacy)

                val telemetry = settings.getAsJsonObject("telemetry") ?: JsonObject()
                telemetry.addProperty("enabled", false)
                settings.add("telemetry", telemetry)

                settingsFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(settings))
            }
        }
    }

    override fun showMessage(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toast(message)
        } else {
            viewModel.viewModelScope.launch(Dispatchers.Main) { toast(message) }
        }
    }

    override suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult {
        val result = ShellUtils.runUbuntu(
            GeminiBridge.primaryWorkspacePath(),
            "/bin/bash",
            "-lc",
            command,
            timeoutSeconds = timeoutSeconds.coerceIn(1, 600),
        )
        return CommandResult(
            output = result.output,
            error = result.error,
            exitCode = result.exitCode,
            timedOut = result.timedOut
        )
    }

    override fun getPrimaryWorkspacePath(): String = GeminiBridge.primaryWorkspacePath()

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return viewModel.tabs.filterIsInstance<EditorTab>().find {
            val tabFile = File(it.file.getAbsolutePath())
            val tabCanonical = runCatching { tabFile.canonicalPath }.getOrDefault(tabFile.absolutePath)
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
