package com.rk.ai.service

import android.os.Looper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.GeminiBridge
import com.rk.ai.geminiDisplayRootFor
import com.rk.ai.geminiIdeWorkspacePath
import com.rk.ai.geminiResolveWorkspacePath
import com.rk.ai.workspacePathForResolution
import com.rk.ai.primaryWorkspacePath
import com.rk.exec.ShellUtils
import com.rk.file.FileWrapper
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.GeminiEditorPatch
import com.rk.utils.toast
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface GeminiNotificationSender {
    fun sendNotification(method: String, params: JsonObject)
}

class GeminiIdeServiceImpl(
    private val viewModel: MainViewModel,
    private val notificationSender: GeminiNotificationSender? = null
) : GeminiIdeService {

    private fun d(msg: String) {
        // Log if needed
    }

    override fun resolvePath(path: String): File? {
        val normalized = path.trim()
        if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized)?.let { return it }
        }
        return geminiResolveWorkspacePath(workspacePathForResolution(), path)
    }

    private fun resolveRelativePathFromOpenEditor(path: String): File? {
        val activeTab = viewModel.currentTab as? EditorTab
        val activeBase = activeTab?.let { File(it.file.getAbsolutePath()).parentFile }
        activeBase?.let { parent ->
            geminiResolveWorkspacePath(workspacePathForResolution(), File(parent, path).path)?.let { return it }
        }

        val exactMatches = viewModel.tabs
            .filterIsInstance<EditorTab>()
            .mapNotNull { tab ->
                val tabFile = File(tab.file.getAbsolutePath())
                if (tabFile.path.endsWith(File.separator + path)) {
                    geminiResolveWorkspacePath(workspacePathForResolution(), tabFile.path)
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
        val root = geminiDisplayRootFor(workspacePathForResolution(), directory)
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

    override fun getFileContent(filePath: String): String? {
        val openTab = findTabByPath(filePath)
        return if (openTab != null) {
            val getEditorText = { openTab.editorState.editor.get()?.text?.toString() }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                getEditorText()
            } else {
                runBlocking(Dispatchers.Main) { getEditorText() }
            }
        } else {
            runCatching { File(filePath).readText() }.getOrNull()
        }
    }

    override fun showPatch(
        filePath: String,
        oldContent: String,
        newContent: String,
        title: String,
        onApply: () -> Unit
    ): Boolean {
        val latch = CountDownLatch(1)
        val accepted = AtomicBoolean(false)

        val shown = runBlocking(Dispatchers.Main) {
            var tab = findTabByPath(filePath)
            if (tab == null) {
                viewModel.editorManager.openFile(FileWrapper(File(filePath)), projectRoot = null, switchToTab = true)
                tab = findTabByPath(filePath)
            }

            if (tab == null) return@runBlocking false

            // Reject existing patch if any
            tab.editorState.pendingGeminiPatch?.reject?.invoke()

            tab.editorState.pendingGeminiPatch =
                GeminiEditorPatch(
                    title = title,
                    filePath = filePath,
                    oldText = oldContent,
                    newText = newContent,
                    apply = {
                        runCatching { onApply() }
                            .onSuccess {
                                accepted.set(true)
                                viewModel.showGeminiSheet = true
                            }
                            .onFailure {
                                toast("Gemini apply failed: ${it.message ?: it::class.java.simpleName}")
                                notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply {
                                    addProperty("filePath", filePath)
                                    addProperty("reason", it.message ?: "apply failed")
                                })
                            }
                        latch.countDown()
                    },
                    reject = {
                        notificationSender?.sendNotification("ide/diffRejected", JsonObject().apply { addProperty("filePath", filePath) })
                        latch.countDown()
                    },
                )
            true
        }

        if (shown) {
            // Wait up to 30 minutes for user to review
            latch.await(30, TimeUnit.MINUTES)
            return accepted.get()
        }
        return false
    }

    override fun writeFile(file: File, content: String) {
        val tab = findTabByPath(file.absolutePath)
        runBlocking(Dispatchers.IO) {
            tab?.saveMutex?.withLock {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            } ?: run {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }
        }

        val updateUi = {
            tab?.let {
                it.editorState.editor.get()?.setText(content)
                it.editorState.content = it.editorState.editor.get()?.text
                it.editorState.updateUndoRedo()
                it.editorState.isDirty = false
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateUi()
        } else {
            runBlocking(Dispatchers.Main) { updateUi() }
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
        runBlocking(Dispatchers.Main) {
            viewModel.editorManager.openFile(FileWrapper(file), projectRoot = null, switchToTab = true)
        }
    }

    override fun getOpenFiles(): List<JsonObject> {
        val current = viewModel.currentTab as? EditorTab
        return viewModel.tabs.filterIsInstance<EditorTab>().map { it.toIdeFileJsonObject(it == current) }
    }

    override fun getActiveFile(): JsonObject? {
        val current = viewModel.currentTab as? EditorTab ?: return null
        return current.toIdeFileJsonObject(active = true).apply {
            val content = if (Looper.myLooper() == Looper.getMainLooper()) {
                current.editorState.editor.get()?.text?.toString().orEmpty()
            } else {
                runBlocking(Dispatchers.Main) { current.editorState.editor.get()?.text?.toString().orEmpty() }
            }
            addProperty("content", content)
        }
    }

    override fun getSelection(): String {
        val current = viewModel.currentTab as? EditorTab
        return if (current == null) "" else {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                current.editorState.editor.get()?.getSelectedText().orEmpty()
            } else {
                runBlocking(Dispatchers.Main) { current.editorState.editor.get()?.getSelectedText().orEmpty() }
            }
        }
    }

    override fun replaceSelection(newContent: String): String {
        val current = viewModel.currentTab as? EditorTab ?: return "no active editor"
        val ok = runBlocking(Dispatchers.Main) {
            val editor = current.editorState.editor.get() ?: return@runBlocking false
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
                ) {
                    runBlocking(Dispatchers.Main) {
                        editor.text.replace(start, end, newContent)
                        current.editorState.isDirty = true
                    }
                }
            true
        }
        return if (ok) "Replacement opened in Xed for user review." else "No editor available."
    }

    override fun insertAtCursor(newContent: String): String {
        val current = viewModel.currentTab as? EditorTab ?: return "no active editor"
        val ok = runBlocking(Dispatchers.Main) {
            val editor = current.editorState.editor.get() ?: return@runBlocking false
            val line = editor.cursor.leftLine
            val column = editor.cursor.leftColumn

            // Reject existing patch if any
            current.editorState.pendingGeminiPatch?.reject?.invoke()

            current.editorState.pendingGeminiPatch =
                GeminiEditorPatch("Review Gemini insertion", current.file.getAbsolutePath(), "", newContent) {
                    runBlocking(Dispatchers.Main) {
                        editor.text.insert(line, column, newContent)
                        current.editorState.isDirty = true
                    }
                }
            true
        }
        return if (ok) "Insertion opened in Xed for user review." else "No editor available."
    }

    override fun saveAll(): String {
        val tabs = viewModel.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
        runBlocking(Dispatchers.Main) {
            tabs.forEach { tab ->
                tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
            }
        }
        // Run saves in parallel on IO dispatcher to avoid blocking the main thread sequentially
        runBlocking(Dispatchers.IO) {
            tabs.forEach { tab -> tab.quickSave() }
        }
        return "saved ${tabs.size} dirty open file(s)"
    }

    override fun showMessage(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toast(message)
        } else {
            runBlocking(Dispatchers.Main) { toast(message) }
        }
    }

    override suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult {
        val result = ShellUtils.runUbuntu(
            primaryWorkspacePath(),
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

    override fun getPrimaryWorkspacePath(): String = primaryWorkspacePath()

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return viewModel.tabs.filterIsInstance<EditorTab>().find {
            val tabFile = File(it.file.getAbsolutePath())
            val tabCanonical = runCatching { tabFile.canonicalPath }.getOrDefault(tabFile.absolutePath)
            tabCanonical == canonical
        }
    }

    private fun EditorTab.toIdeFileJsonObject(active: Boolean): JsonObject {
        val editor = if (Looper.myLooper() == Looper.getMainLooper()) {
            editorState.editor.get()
        } else {
            runBlocking(Dispatchers.Main) { editorState.editor.get() }
        }
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
