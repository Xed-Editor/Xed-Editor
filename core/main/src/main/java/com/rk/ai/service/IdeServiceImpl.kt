package com.rk.ai.service

import android.os.Looper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.IdeBridge
import com.rk.ai.displayRootFor
import com.rk.ai.ideWorkspacePath
import com.rk.ai.resolveWorkspacePath
import com.rk.ai.session.AiSessionManager
import com.rk.lsp.applyFormattingOptions
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.document.applyEdits
import com.rk.exec.ShellUtils
import com.rk.file.FileWrapper
import com.rk.file.toFileWrapper
import com.rk.search.SearchViewModel
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.EditorPatch
import com.rk.utils.toast
import com.rk.utils.application
import io.github.rosemoe.sora.lsp.events.format.fullFormatting
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

interface IdeNotificationSender {
    fun sendNotification(method: String, params: JsonObject)
}

class IdeServiceImpl(
    private val viewModel: MainViewModel,
    private val notificationSender: IdeNotificationSender? = null
) : IdeService {

    override fun resolvePath(path: String): File? {
        val normalized = path.trim()
        if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized)?.let { return it }
        }
        return resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
    }

    private fun resolveRelativePathFromOpenEditor(path: String): File? {
        val activeTab = viewModel.currentTab as? EditorTab
        val activeBase = activeTab?.let { File(it.file.getAbsolutePath()).parentFile }
        activeBase?.let { parent ->
            resolveWorkspacePath(IdeBridge.workspacePathForResolution(), File(parent, path).path)?.let { return it }
        }

        val exactMatches = viewModel.tabs
            .filterIsInstance<EditorTab>()
            .mapNotNull { tab ->
                val tabFile = File(tab.file.getAbsolutePath())
                if (tabFile.path.endsWith(File.separator + path)) {
                    resolveWorkspacePath(IdeBridge.workspacePathForResolution(), tabFile.path)
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
        val root = displayRootFor(IdeBridge.workspacePathForResolution(), directory)
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
                // openFile is a suspend function that handles its own main thread switching
                viewModel.editorManager.openFile(FileWrapper(File(filePath)), projectRoot = null, switchToTab = true)
                // Wait briefly for the tab to be added to viewModel.tabs if it's not immediate
                 var attempts = 0
                 while (tab == null && attempts < 20) {
                     kotlinx.coroutines.delay(50)
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

            // Reject existing patch if any
            tab.editorState.pendingGeminiPatch?.reject?.invoke()

            tab.editorState.pendingGeminiPatch =
                EditorPatch(
                    title = title,
                    filePath = filePath,
                    oldText = oldContent,
                    newText = newContent,
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
                // Explicitly refresh the tab to sync state and update lastModifiedAt
                it.refresh()
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

            // Reject existing patch if any
            current.editorState.pendingGeminiPatch?.reject?.invoke()

            current.editorState.pendingGeminiPatch =
                EditorPatch(
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

            // Reject existing patch if any
            current.editorState.pendingGeminiPatch?.reject?.invoke()

            current.editorState.pendingGeminiPatch =
                EditorPatch(
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

    override suspend fun searchCode(query: String, limit: Int): JsonArray {
        val results = JsonArray()
        val searchViewModel = SearchViewModel()
        val projectRoot = File(IdeBridge.primaryWorkspacePath()).toFileWrapper()
        
        withContext(Dispatchers.IO) {
            val app = application!!
            searchViewModel.searchCode(
                context = app,
                mainViewModel = viewModel,
                projectRoot = projectRoot,
                query = query,
                useIndex = Settings.always_index_projects
            ).take(limit).collect { item ->
                results.add(JsonObject().apply {
                    addProperty("path", item.file.getAbsolutePath())
                    addProperty("line", item.line + 1)
                    addProperty("column", item.column + 1)
                    addProperty("snippet", item.snippet.text.toString())
                })
            }
        }
        return results
    }

    override suspend fun findFiles(query: String, limit: Int): JsonArray {
        val results = JsonArray()
        val searchViewModel = SearchViewModel()
        val projectRoot = File(IdeBridge.primaryWorkspacePath()).toFileWrapper()

        withContext(Dispatchers.IO) {
            val app = application!!
            val fileMetas = searchViewModel.searchFileName(
                context = app,
                projectRoot = projectRoot,
                query = query,
                useIndex = Settings.always_index_projects
            )
            fileMetas.take(limit).forEach { meta ->
                results.add(JsonObject().apply {
                    addProperty("path", meta.path)
                    addProperty("name", meta.fileName)
                })
            }
        }
        return results
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
            IdeBridge.primaryWorkspacePath(),
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

    override fun getPrimaryWorkspacePath(): String = IdeBridge.primaryWorkspacePath()

    override suspend fun getDiagnostics(filePath: String): JsonArray {
        val results = JsonArray()
        val tab = findTabByPath(filePath) ?: return results
        
        withContext(Dispatchers.Main) {
            tab.editorState.diagnostics.forEach { diag ->
                results.add(JsonObject().apply {
                    val messageStr = if (diag.message.isLeft) diag.message.left else diag.message.right.toString()
                    addProperty("message", messageStr)
                    addProperty("severity", diag.severity.name)
                    add("range", JsonObject().apply {
                        add("start", JsonObject().apply {
                            addProperty("line", diag.range.start.line + 1)
                            addProperty("character", diag.range.start.character + 1)
                        })
                        add("end", JsonObject().apply {
                            addProperty("line", diag.range.end.line + 1)
                            addProperty("character", diag.range.end.character + 1)
                        })
                    })
                    diag.code?.let { code ->
                        val codeValue = if (code.isLeft) code.left else code.right.toString()
                        addProperty("code", codeValue)
                    }
                    diag.source?.let { addProperty("source", it) }
                })
            }
        }
        return results
    }

    override suspend fun findDefinitions(filePath: String, line: Int, column: Int): JsonArray {
        val results = JsonArray()
        val tab = findTabByPath(filePath) ?: return results
        val connector = tab.lspConnector ?: return results
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return results

        withContext(Dispatchers.IO) {
            runCatching {
                // Temporary move cursor to requested position if needed, or just use Position object
                // requestDefinition in LspConnector uses editor.cursor, so we might need to set it
                withContext(Dispatchers.Main) {
                    editor.cursor.set(line - 1, column - 1)
                }
                
                val either = connector.requestDefinition(editor)
                val locations = if (either.isLeft) either.left else either.right.map { 
                    org.eclipse.lsp4j.Location(it.targetUri, it.targetSelectionRange)
                }

                locations.forEach { loc ->
                    results.add(JsonObject().apply {
                        addProperty("uri", loc.uri)
                        add("range", JsonObject().apply {
                            add("start", JsonObject().apply {
                                addProperty("line", loc.range.start.line + 1)
                                addProperty("character", loc.range.start.character + 1)
                            })
                            add("end", JsonObject().apply {
                                addProperty("line", loc.range.end.line + 1)
                                addProperty("character", loc.range.end.character + 1)
                            })
                        })
                    })
                }
            }
        }
        return results
    }

    override suspend fun findReferences(filePath: String, line: Int, column: Int): JsonArray {
        val results = JsonArray()
        val tab = findTabByPath(filePath) ?: return results
        val connector = tab.lspConnector ?: return results
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return results

        withContext(Dispatchers.IO) {
            runCatching {
                withContext(Dispatchers.Main) {
                    editor.cursor.set(line - 1, column - 1)
                }
                
                val locations = connector.requestReferences(editor)
                locations.forEach { loc ->
                    loc?.let { l ->
                        results.add(JsonObject().apply {
                            addProperty("uri", l.uri)
                            add("range", JsonObject().apply {
                                add("start", JsonObject().apply {
                                    addProperty("line", l.range.start.line + 1)
                                    addProperty("character", l.range.start.character + 1)
                                })
                                add("end", JsonObject().apply {
                                    addProperty("line", l.range.end.line + 1)
                                    addProperty("character", l.range.end.character + 1)
                                })
                            })
                        })
                    }
                }
            }
        }
        return results
    }

    override fun renameSymbol(filePath: String, line: Int, column: Int, newName: String) {
        viewModel.viewModelScope.launch(Dispatchers.Main) {
            val tab = findTabByPath(filePath) ?: return@launch
            val connector = tab.lspConnector ?: return@launch
            val editor = tab.editorState.editor.get() ?: return@launch

            withContext(Dispatchers.IO) {
                runCatching {
                    withContext(Dispatchers.Main) {
                        editor.cursor.set(line - 1, column - 1)
                    }
                    val workspaceEdit = connector.requestRenameSymbol(editor, newName)
                    val changes = workspaceEdit.changes
                    
                    // For now, only support edits in the current file for the programmatic tool
                    // until we have a better multi-file patch system
                    val edits = changes[tab.file.toUri().toString()]
                    if (edits != null) {
                        // Apply edits to a copy of text to show a diff
                        val oldText = withContext(Dispatchers.Main) { editor.text.toString() }
                        // This is tricky because Sora Editor's Content doesn't have a simple "apply edits" without being attached
                        // But we can use the event system or just wait for user to apply
                        
                        // Propose as a patch
                        // To get the new text, we'd need to simulate the edits.
                        // For now, let's just use the event emitter to apply it directly if auto-apply is on,
                        // or show a toast that multi-file rename is complex.
                        
                        withContext(Dispatchers.Main) {
                            connector.getEventManager()!!.emitAsync(EventType.applyEdits) {
                                put("edits", edits)
                                put(editor.text)
                            }
                            toast("Symbol renamed (local file only)")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            toast("Rename symbol not found in current file or multiple files affected.")
                        }
                    }
                }
            }
        }
    }

    override suspend fun formatDocument(filePath: String) {
        val tab = findTabByPath(filePath) ?: return
        val connector = tab.lspConnector ?: return
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return
        val eventManager = connector.getEventManager() ?: return

        withContext(Dispatchers.Main) {
            applyFormattingOptions(eventManager, tab)
            eventManager.emitAsync(EventType.fullFormatting, editor.text)
        }
    }

    override suspend fun getGitStatus(workspacePath: String): JsonObject {
        val result = JsonObject()
        if (workspacePath.isBlank()) return result.apply { addProperty("error", "workspacePath required") }

        withContext(Dispatchers.IO) {
            runCatching {
                val repoDir = File(workspacePath)
                val builder = FileRepositoryBuilder().readEnvironment().findGitDir(repoDir)
                val repo = builder.build()
                if (repo.directory == null) {
                    result.addProperty("error", "not a git repository")
                    return@withContext
                }

                Git(repo).use { git ->
                    val status = git.status().call()
                    val branch = repo.branch
                    result.addProperty("branch", branch ?: "HEAD")

                    result.add("changes", JsonArray().apply {
                        status.added.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "added") }) }
                        status.changed.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "staged") }) }
                        status.modified.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "modified") }) }
                        status.removed.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "removed") }) }
                        status.untracked.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "untracked") }) }
                        status.conflicting.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "conflicting") }) }
                    })

                    result.addProperty("totalChanges", result.getAsJsonArray("changes").size())
                }
            }.onFailure {
                result.addProperty("error", it.message ?: "git error")
            }
        }
        return result
    }

    override suspend fun createFile(filePath: String, content: String?): String {
        val file = resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace: $filePath")
        if (file.exists()) throw IllegalArgumentException("file already exists: $filePath")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (content != null) {
                file.writeText(content, Charsets.UTF_8)
            } else {
                file.createNewFile()
            }
            refreshEditors(filePath = file.absolutePath, force = true)
        }
        return "created ${file.absolutePath}"
    }

    override suspend fun deleteFile(filePath: String): String {
        val file = resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace: $filePath")
        if (!file.exists()) throw IllegalArgumentException("file not found: $filePath")
        if (!file.isFile) throw IllegalArgumentException("not a file: $filePath")

        withContext(Dispatchers.Main) {
            val tab = findTabByPath(file.absolutePath)
            if (tab != null) {
                viewModel.tabManager.removeTab(tab)
            }
        }

        withContext(Dispatchers.IO) {
            file.delete()
        }
        return "deleted ${file.absolutePath}"
    }

    override suspend fun getTerminalOutput(lines: Int?): String {
        val session = AiSessionManager.session
        if (session == null || !session.isRunning) return "No active Gemini terminal session"
        return withContext(Dispatchers.IO) {
            val emulator = session.emulator ?: return@withContext "Terminal emulator not available"
            val screen = emulator.screen
            val full = screen.getTranscriptTextWithoutJoinedLines()
            if (lines != null && lines > 0) {
                val all = full.split("\n")
                all.takeLast(lines.coerceAtLeast(1)).joinToString("\n")
            } else {
                full
            }
        }
    }

    override suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String {
        val dir = resolvePath(path) ?: throw IllegalArgumentException("path outside workspace: $path")
        if (!dir.exists() || !dir.isDirectory) throw IllegalArgumentException("not a directory: $path")

        val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".dex", ".cache")
        val output = StringBuilder()
        val count = intArrayOf(0)

        fun walk(current: File, depth: Int) {
            if (count[0] >= maxItems) return
            if (depth > maxDepth) return
            val indent = "  ".repeat(depth)
            val children = current.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: return

            for (child in children) {
                if (count[0] >= maxItems) return
                if (child.name in ignored) continue
                if (child.isHidden && child.name != ".env") continue

                val prefix = if (child.isDirectory) "[D]" else "[F]"
                output.appendLine("$indent$prefix ${child.name}")
                count[0]++

                if (child.isDirectory && depth < maxDepth) {
                    walk(child, depth + 1)
                }
            }
        }

        withContext(Dispatchers.IO) {
            output.appendLine("[D] ${dir.name}/")
            walk(dir, 1)
            if (count[0] >= maxItems) output.appendLine("  ... (truncated at $maxItems items)")
        }

        return output.toString()
    }

    override suspend fun getSymbolUnderCursor(): JsonObject {
        val tab = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab } ?: return JsonObject()
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return JsonObject()
        return withContext(Dispatchers.Main) {
            val line = editor.cursor.leftLine
            val column = editor.cursor.leftColumn
            val text = editor.text.toString()
            val lines = text.split("\n")
            val currentLine = lines.getOrNull(line) ?: ""
            // Extract surrounding context (5 lines before and after)
            val contextStart = (line - 5).coerceAtLeast(0)
            val contextEnd = (line + 5).coerceAtMost(lines.size - 1)
            val contextLines = lines.subList(contextStart, contextEnd + 1)
            // Try to find enclosing function/class by scanning backwards
            val scopeLines = mutableListOf<String>()
            for (i in line downTo 0) {
                val l = lines.getOrNull(i) ?: break
                scopeLines.add(l.trim())
                if (l.contains("fun ") || l.contains("class ") || l.contains("def ") || l.contains("function ")) break
            }
            val selected = editor.getSelectedText().orEmpty()
            JsonObject().apply {
                addProperty("filePath", tab.file.getAbsolutePath())
                addProperty("line", line + 1)
                addProperty("column", column + 1)
                addProperty("currentLine", currentLine)
                addProperty("selectedText", selected.take(1024))
                addProperty("context", contextLines.joinToString("\n"))
                addProperty("enclosingScope", scopeLines.reversed().joinToString("\n").take(1024))
            }
        }
    }

    override suspend fun getProjectConfig(workspacePath: String): JsonObject {
        val result = JsonObject()
        val root = if (workspacePath.isNotBlank()) File(workspacePath) else File(getPrimaryWorkspacePath())
        if (!root.exists() || !root.isDirectory) return result.apply { addProperty("error", "invalid workspace") }

        withContext(Dispatchers.IO) {
            val files = root.listFiles()?.map { it.name }?.toSet() ?: emptySet()

            if ("package.json" in files) {
                result.addProperty("language", "JavaScript/TypeScript")
                result.addProperty("buildSystem", "npm/yarn/pnpm")
                runCatching {
                    val pkg = com.google.gson.JsonParser.parseString(File(root, "package.json").readText()).asJsonObject
                    pkg.get("scripts")?.asJsonObject?.let { scripts ->
                        result.add("scripts", scripts)
                    }
                }
            } else if ("pubspec.yaml" in files) {
                result.addProperty("language", "Dart")
                result.addProperty("buildSystem", "pub")
            } else if ("build.gradle.kts" in files || "build.gradle" in files) {
                result.addProperty("language", "Kotlin/Java")
                result.addProperty("buildSystem", "Gradle")
            } else if ("Cargo.toml" in files) {
                result.addProperty("language", "Rust")
                result.addProperty("buildSystem", "Cargo")
            } else if ("CMakeLists.txt" in files) {
                result.addProperty("language", "C/C++")
                result.addProperty("buildSystem", "CMake")
            } else if ("go.mod" in files) {
                result.addProperty("language", "Go")
                result.addProperty("buildSystem", "go mod")
            } else if ("requirements.txt" in files || "setup.py" in files || "pyproject.toml" in files) {
                result.addProperty("language", "Python")
                result.addProperty("buildSystem", "pip/poetry")
            } else {
                result.addProperty("language", "unknown")
                result.addProperty("buildSystem", "unknown")
            }

            result.addProperty("workspace", root.absolutePath)
            result.add("files", com.google.gson.JsonArray().apply {
                files.filter { !it.startsWith(".") }.take(50).forEach { add(it) }
            })
        }
        return result
    }

    override suspend fun getGitDiff(workspacePath: String): String {
        if (workspacePath.isBlank()) return "workspacePath required"
        return withContext(Dispatchers.IO) {
            runCatching {
                val repoDir = File(workspacePath)
                val builder = FileRepositoryBuilder().readEnvironment().findGitDir(repoDir)
                val repo = builder.build()
                if (repo.directory == null) return@withContext "not a git repository"
                Git(repo).use { git ->
                    val diff = git.diff().call()
                    val baos = java.io.ByteArrayOutputStream()
                    val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
                    formatter.setRepository(repo)
                    formatter.format(diff)
                    formatter.close()
                    val text = baos.toString(Charsets.UTF_8.name())
                    text.ifEmpty { "no changes" }
                }
            }.getOrElse { "error: ${it.message}" }
        }
    }

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
