package com.rk.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.activities.main.MainViewModel
import com.rk.exec.ShellUtils
import com.rk.file.FileWrapper
import com.rk.tabs.editor.GeminiEditorPatch
import com.rk.tabs.editor.EditorTab
import com.rk.utils.toast
import fi.iki.elonen.NanoHTTPD
import com.rk.utils.getTempDir
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object GeminiBridge {
    data class Info(val url: String, val port: Int, val token: String, val workspacePath: String)

    private var server: BridgeServer? = null
    private val secureRandom = SecureRandom()
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String): Info {
        val existing = server
        if (existing != null) {
            existing.viewModel = viewModel
            existing.workspacePath = workspacePath
            existing.writeDiscoveryFile()
            return existing.info()
        }

        val token = newToken()
        val created = BridgeServer(viewModel, token, workspacePath)
        created.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        created.writeDiscoveryFile()
        server = created
        return created.info()
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private class BridgeServer(var viewModel: MainViewModel, private val token: String, var workspacePath: String) :
        NanoHTTPD("127.0.0.1", 0) {
        private val sessionId = UUID.randomUUID().toString()
        private val sseClients = ConcurrentHashMap<String, PrintWriter>()

        fun info(): Info = Info(url = "http://127.0.0.1:$listeningPort", port = listeningPort, token = token, workspacePath)

        fun writeDiscoveryFile() {
            runCatching {
                val dir = File(getTempDir(), "gemini/ide")
                dir.mkdirs()
                dir.listFiles { file -> file.name.startsWith("gemini-ide-server-${android.os.Process.myPid()}-") && file.name.endsWith(".json") }
                    ?.filter { it.name != "gemini-ide-server-${android.os.Process.myPid()}-$listeningPort.json" }
                    ?.forEach { it.delete() }
                val file = File(dir, "gemini-ide-server-${android.os.Process.myPid()}-$listeningPort.json")
                val discovery = JsonObject().apply {
                    addProperty("port", listeningPort)
                    addProperty("workspacePath", geminiIdeWorkspacePath(workspacePath))
                    addProperty("authToken", token)
                    add("ideInfo", JsonObject().apply {
                        addProperty("name", "xed")
                        addProperty("displayName", "Xed-Editor")
                    })
                }
                file.writeText(gson.toJson(discovery))
                file.setReadable(false, false)
                file.setWritable(false, false)
                file.setReadable(true, true)
                file.setWritable(true, true)
            }
        }

        override fun serve(session: IHTTPSession): Response {
            if (!hasValidHost(session)) {
                return json(Response.Status.FORBIDDEN, error(null, -32003, "invalid host"))
            }
            if (!isAuthorized(session)) {
                return json(Response.Status.UNAUTHORIZED, error(null, -32001, "unauthorized"))
            }

            return when (session.uri) {
                "/health" -> json(Response.Status.OK, "{\"ok\":true}")
                "/context" -> json(Response.Status.OK, ideContextJson())
                "/refresh" -> {
                    refreshEditors()
                    json(Response.Status.OK, "{\"ok\":true}")
                }
                "/external-editor" -> serveExternalEditor(session)
                "/mcp" -> serveMcp(session)
                else -> json(Response.Status.NOT_FOUND, error(null, -32601, "not_found"))
            }
        }

        private fun serveExternalEditor(session: IHTTPSession): Response {
            if (session.method != Method.POST) {
                return json(Response.Status.BAD_REQUEST, error(null, -32601, "method_not_allowed"))
            }

            val body = mutableMapOf<String, String>()
            runCatching { session.parseBody(body) }.getOrElse {
                return json(Response.Status.BAD_REQUEST, error(null, -32700, it.message ?: "invalid request"))
            }
            val request = runCatching { JsonParser.parseString(body["postData"].orEmpty()).asJsonObject }.getOrNull()
                ?: return json(Response.Status.BAD_REQUEST, error(null, -32700, "parse error"))

            val newPath = request.get("newPath")?.asString.orEmpty()
            val oldPath = request.get("oldPath")?.asString?.takeIf { it.isNotBlank() }
            if (newPath.isBlank()) return json(Response.Status.BAD_REQUEST, error(null, -32602, "newPath required"))

            val newFile = File(newPath)
            val oldText = oldPath?.let { runCatching { File(it).readText() }.getOrDefault("") }.orEmpty()
            val newText = runCatching { newFile.readText() }.getOrElse {
                return json(Response.Status.BAD_REQUEST, error(null, -32602, "cannot read editable file"))
            }

            val latch = CountDownLatch(1)
            val accepted = AtomicBoolean(false)
            val shown = runBlocking(Dispatchers.Main) {
                val tab = (viewModel.currentTab as? EditorTab)
                    ?: viewModel.tabs.filterIsInstance<EditorTab>().firstOrNull()
                    ?: return@runBlocking false
                tab.editorState.pendingGeminiPatch =
                    GeminiEditorPatch(
                        title = "Review Gemini external editor change",
                        filePath = newPath,
                        oldText = oldText,
                        newText = newText,
                        reject = {
                            accepted.set(false)
                            latch.countDown()
                        },
                        apply = {
                            runCatching { newFile.writeText(newText) }
                            accepted.set(true)
                            latch.countDown()
                        },
                    )
                true
            }

            if (!shown) return json(Response.Status.BAD_REQUEST, error(null, -32602, "no Xed editor tab available"))
            val completed = latch.await(30, TimeUnit.MINUTES)
            if (!completed) return json(Response.Status.OK, error(null, -32000, "external editor timed out"))
            return json(Response.Status.OK, gson.toJson(JsonObject().apply { addProperty("accepted", accepted.get()) }))
        }

        private fun serveMcp(session: IHTTPSession): Response {
            if (session.method == Method.GET) {
                return serveMcpStream(session)
            }

            val body = mutableMapOf<String, String>()
            runCatching { session.parseBody(body) }.getOrElse { return json(Response.Status.BAD_REQUEST, error(null, -32700, it.message ?: "invalid request")) }
            val raw = body["postData"].orEmpty()
            val request = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: return json(Response.Status.BAD_REQUEST, error(null, -32700, "parse error"))

            val id = request.get("id")?.toString() ?: "null"
            val method = request.get("method")?.asString.orEmpty()
            val response =
                when (method) {
                    "initialize" -> initializeResult(id)
                    "notifications/initialized" -> ""
                    "ping" -> result(id, JsonObject())
                    "tools/list" -> toolsListResult(id)
                    "tools/call" -> toolsCallResult(id, request)
                    else -> error(id, -32601, "method not found: $method")
                }

            val nanoResponse = json(Response.Status.OK, response)
            nanoResponse.addHeader("mcp-session-id", sessionId)
            return nanoResponse
        }

        private fun serveMcpStream(session: IHTTPSession): Response {
            val requestedSessionId = session.headers["mcp-session-id"] ?: sessionId
            val input = PipedInputStream()
            val output = PipedOutputStream(input)
            val writer = PrintWriter(output, true)
            sseClients[requestedSessionId] = writer
            writer.print(": connected\n\n")
            writer.flush()
            runCatching {
                writer.print("event: message\n")
                writer.print("data: ${contextUpdateNotificationJson()}\n\n")
                writer.flush()
            }
            return newChunkedResponse(Response.Status.OK, "text/event-stream", input).apply {
                addHeader("mcp-session-id", requestedSessionId)
                addHeader("Cache-Control", "no-store")
                addHeader("Connection", "keep-alive")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }

        private fun sendMcpNotification(method: String, params: JsonObject) {
            val notification = notificationJson(method, params)
            val deadClients = mutableListOf<String>()
            sseClients.forEach { (id, writer) ->
                runCatching {
                    writer.print("event: message\n")
                    writer.print("data: $notification\n\n")
                    writer.flush()
                    if (writer.checkError()) deadClients.add(id)
                }.onFailure { deadClients.add(id) }
            }
            deadClients.forEach { id -> sseClients.remove(id)?.close() }
        }

        private fun notificationJson(method: String, params: JsonObject): String =
            gson.toJson(JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("method", method)
                add("params", params)
            })

        private fun contextUpdateNotificationJson(): String =
            notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)

        private fun initializeResult(id: String): String {
            val result = JsonObject().apply {
                addProperty("protocolVersion", "2025-06-18")
                add("capabilities", JsonObject().apply { add("tools", JsonObject()) })
                add("serverInfo", JsonObject().apply {
                    addProperty("name", "xed-gemini-ide-companion")
                    addProperty("version", "1.0.0")
                })
            }
            return result(id, result)
        }

        private fun toolsListResult(id: String): String =
            result(id, JsonObject().apply { add("tools", GeminiMcpTools.list()) })

        private fun toolsCallResult(id: String, request: JsonObject): String {
            val params = request.getAsJsonObject("params") ?: return error(id, -32602, "missing params")
            val name = params.get("name")?.asString.orEmpty()
            val args = params.getAsJsonObject("arguments") ?: JsonObject()
            return when (name) {
                "openDiff" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    val newContent = args.get("newContent")?.asString.orEmpty()
                    if (filePath.isBlank()) return error(id, -32602, "filePath required")
                    val file = resolveWorkspacePath(filePath) ?: return error(id, -32602, "path outside workspace")
                    val oldContent = openEditorContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
                    val shown = showPendingPatch(file.absolutePath, oldContent, newContent) {
                        file.parentFile?.mkdirs()
                        file.writeText(newContent)
                        refreshEditor(file.absolutePath, force = true)
                        refreshEditors(onlyClean = true)
                        sendMcpNotification("ide/diffAccepted", JsonObject().apply {
                            addProperty("filePath", file.absolutePath)
                            addProperty("content", newContent)
                        })
                    }
                    if (shown) callToolEmptyResult(id) else {
                        file.parentFile?.mkdirs()
                        file.writeText(newContent)
                        refreshEditors(onlyClean = true)
                        sendMcpNotification("ide/diffAccepted", JsonObject().apply {
                            addProperty("filePath", file.absolutePath)
                            addProperty("content", newContent)
                        })
                        callToolTextResult(id, "No open editor tab was available; wrote ${file.absolutePath} directly.")
                    }
                }
                "closeDiff" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    val file = resolveWorkspacePath(filePath) ?: return error(id, -32602, "path outside workspace")
                    val content = runCatching { file.readText() }.getOrDefault("")
                    callToolTextResult(id, gson.toJson(JsonObject().apply { addProperty("content", content) }))
                }
                "readFile" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    if (filePath.isBlank()) return error(id, -32602, "filePath required")
                    val file = resolveWorkspacePath(filePath) ?: return error(id, -32602, "path outside workspace")
                    val absolutePath = file.absolutePath
                    val openTab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == absolutePath }
                    val content = if (openTab != null) {
                        runBlocking(Dispatchers.Main) { openTab.editorState.editor.get()?.text?.toString() } ?: runCatching { file.readText() }.getOrDefault("")
                    } else {
                        runCatching { file.readText() }.getOrDefault("")
                    }
                    callToolTextResult(id, content)
                }
                "listFiles" -> {
                    val dirPath = args.get("directoryPath")?.asString.orEmpty()
                    val dir = resolveWorkspacePath(dirPath) ?: return error(id, -32602, "path outside workspace")
                    val recursive = args.get("recursive")?.asBoolean ?: false
                    val maxFiles = args.get("maxFiles")?.asInt ?: 500
                    val files = listWorkspaceFiles(dir, recursive, maxFiles.coerceIn(1, 5000))
                    callToolTextResult(id, files)
                }
                "getOpenFiles" -> callToolTextResult(id, gson.toJson(JsonArray().apply {
                    viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab ->
                        add(JsonObject().apply {
                            addProperty("path", tab.file.getAbsolutePath())
                            addProperty("isActive", tab == viewModel.currentTab)
                        })
                    }
                }))
                "getActiveFile" -> {
                    val current = viewModel.currentTab as? EditorTab ?: return callToolTextResult(id, "{}")
                    callToolTextResult(id, gson.toJson(current.toIdeFileJsonObject(active = true).apply {
                        addProperty("content", runBlocking(Dispatchers.Main) { current.editorState.editor.get()?.text?.toString() }.orEmpty())
                    }))
                }
                "getSelection" -> {
                    val current = viewModel.currentTab as? EditorTab
                    val selected = current?.let { runBlocking(Dispatchers.Main) { it.editorState.editor.get()?.getSelectedText().orEmpty() } }.orEmpty()
                    callToolTextResult(id, selected)
                }
                "replaceSelection" -> {
                    val newContent = args.get("newContent")?.asString.orEmpty()
                    val current = viewModel.currentTab as? EditorTab ?: return error(id, -32602, "no active editor")
                    val ok = runBlocking(Dispatchers.Main) {
                        val editor = current.editorState.editor.get() ?: return@runBlocking false
                        val hasSelection = editor.isTextSelected
                        val start = if (hasSelection) minOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex) else 0
                        val end = if (hasSelection) maxOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex) else editor.text.toString().length
                        val oldText = editor.text.substring(start, end)
                        current.editorState.pendingGeminiPatch =
                            GeminiEditorPatch(
                                title = if (hasSelection) "Review Gemini selection replacement" else "Review Gemini file replacement",
                                filePath = current.file.getAbsolutePath(),
                                oldText = oldText,
                                newText = newContent,
                            ) {
                                editor.text.replace(start, end, newContent)
                                current.editorState.isDirty = true
                            }
                        true
                    }
                    callToolTextResult(id, if (ok) "Replacement opened in Xed for user review." else "No editor available.")
                }
                "insertAtCursor" -> {
                    val newContent = args.get("newContent")?.asString.orEmpty()
                    val current = viewModel.currentTab as? EditorTab ?: return error(id, -32602, "no active editor")
                    val ok = runBlocking(Dispatchers.Main) {
                        val editor = current.editorState.editor.get() ?: return@runBlocking false
                        val line = editor.cursor.leftLine
                        val column = editor.cursor.leftColumn
                        current.editorState.pendingGeminiPatch =
                            GeminiEditorPatch("Review Gemini insertion", current.file.getAbsolutePath(), "", newContent) {
                                editor.text.insert(line, column, newContent)
                                current.editorState.isDirty = true
                            }
                        true
                    }
                    callToolTextResult(id, if (ok) "Insertion opened in Xed for user review." else "No editor available.")
                }
                "openFile" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    if (filePath.isBlank()) return error(id, -32602, "filePath required")
                    val file = resolveWorkspacePath(filePath) ?: return error(id, -32602, "path outside workspace")
                    runBlocking(Dispatchers.Main) {
                        viewModel.editorManager.openFile(FileWrapper(file), projectRoot = null, switchToTab = true)
                    }
                    sendMcpNotification("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)
                    callToolTextResult(id, "opened ${file.absolutePath}")
                }
                "writeFile" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    val content = args.get("content")?.asString.orEmpty()
                    if (filePath.isBlank()) return error(id, -32602, "filePath required")
                    val file = resolveWorkspacePath(filePath) ?: return error(id, -32602, "path outside workspace")
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    val tab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == file.absolutePath }
                    runBlocking(Dispatchers.Main) {
                        tab?.let {
                            it.editorState.editor.get()?.setText(content)
                            it.editorState.content = it.editorState.editor.get()?.text
                            it.editorState.updateUndoRedo()
                            it.editorState.isDirty = false
                        }
                    }
                    callToolTextResult(id, "wrote and refreshed ${file.absolutePath}")
                }
                "saveOpenFiles" -> {
                    val tabs = viewModel.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
                    runBlocking(Dispatchers.Main) {
                        tabs.forEach { tab ->
                            tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
                        }
                    }
                    runBlocking { tabs.forEach { tab -> tab.quickSave() } }
                    callToolTextResult(id, "saved ${tabs.size} dirty open file(s)")
                }
                "refreshOpenEditors" -> {
                    refreshEditors(onlyClean = true)
                    callToolTextResult(id, "refreshed non-dirty open editor tabs")
                }
                "showMessage" -> {
                    val message = args.get("message")?.asString.orEmpty()
                    runBlocking(Dispatchers.Main) { toast(message) }
                    callToolTextResult(id, "shown")
                }
                "runCommand" -> {
                    val command = args.get("command")?.asString.orEmpty()
                    if (command.isBlank()) return error(id, -32602, "command required")
                    val timeout = args.get("timeoutSeconds")?.asLong ?: 120L
                    val result = runBlocking { ShellUtils.runUbuntu(workspacePath, "/bin/bash", "-lc", command, timeoutSeconds = timeout.coerceIn(1, 600)) }
                    callToolTextResult(id, buildString {
                        if (result.output.isNotBlank()) appendLine(result.output)
                        if (result.error.isNotBlank()) appendLine(result.error)
                        append("exit ${result.exitCode}")
                        if (result.timedOut) append(" (timed out)")
                    })
                }
                "refreshFile" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    val file = resolveWorkspacePath(filePath) ?: return error(id, -32602, "path outside workspace")
                    val refreshed = refreshEditor(file.absolutePath, force = false)
                    callToolTextResult(id, if (refreshed) "refreshed" else "file is not open or has unsaved changes")
                }
                else -> error(id, -32601, "unknown tool: $name")
            }
        }

        private fun resolveWorkspacePath(path: String): File? = geminiResolveWorkspacePath(workspacePath, path)

        private fun listWorkspaceFiles(dir: File, recursive: Boolean, maxFiles: Int): String {
            if (!dir.exists() || !dir.isDirectory) return ""
            val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules")
            val root = geminiDisplayRootFor(workspacePath, dir)
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

            visit(dir)
            return output.joinToString("\n")
        }

        private fun showPendingPatch(filePath: String, oldContent: String, newContent: String, apply: () -> Unit): Boolean =
            runBlocking(Dispatchers.Main) {
                val tab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath }
                    ?: (viewModel.currentTab as? EditorTab)
                    ?: run {
                        viewModel.editorManager.openFile(FileWrapper(File(filePath)), projectRoot = null, switchToTab = true)
                        viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath }
                    }
                    ?: return@runBlocking false
                tab.editorState.pendingGeminiPatch =
                    GeminiEditorPatch(
                        title = "Review Gemini file change",
                        filePath = filePath,
                        oldText = oldContent,
                        newText = newContent,
                        apply = {
                            runCatching {
                                apply()
                                viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath }?.editorState?.showGeminiAssistant = true
                            }.onFailure {
                                toast("Gemini apply failed: ${it.message ?: it::class.java.simpleName}")
                                sendMcpNotification("ide/diffRejected", JsonObject().apply {
                                    addProperty("filePath", filePath)
                                    addProperty("reason", it.message ?: "apply failed")
                                })
                            }
                        },
                        reject = {
                            sendMcpNotification("ide/diffRejected", JsonObject().apply { addProperty("filePath", filePath) })
                        },
                    )
                true
            }

        private fun openEditorContent(filePath: String): String? {
            val tab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath } ?: return null
            return runBlocking(Dispatchers.Main) { tab.editorState.editor.get()?.text?.toString() }
        }

        private fun refreshEditor(filePath: String, force: Boolean): Boolean {
            val tab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath } ?: return false
            if (!force && tab.editorState.isDirty) return false
            tab.refresh()
            return true
        }

        private fun refreshEditors(onlyClean: Boolean = true) {
            viewModel.tabs.filterIsInstance<EditorTab>().forEach {
                if (!onlyClean || !it.editorState.isDirty) it.refresh()
            }
        }

        private fun isAuthorized(session: IHTTPSession): Boolean {
            val queryToken = session.parameters["token"]?.firstOrNull()
            val headerToken = session.headers["authorization"]?.removePrefix("Bearer ")
            return queryToken == token || headerToken == token
        }

        private fun hasValidHost(session: IHTTPSession): Boolean {
            val host = session.headers["host"] ?: return true
            val hostname = host.substringBefore(":")
            return hostname == "127.0.0.1" || hostname == "localhost" || hostname == "[::1]"
        }

        private fun ideContextJson(): String = runBlocking {
            val current = viewModel.currentTab as? EditorTab
            val openFiles = viewModel.tabs.filterIsInstance<EditorTab>()
            val context = JsonObject().apply {
                add("workspaceState", JsonObject().apply {
                    addProperty("isTrusted", true)
                    add("openFiles", JsonArray().apply {
                        openFiles.forEach { add(it.toIdeFileJsonObject(it == current)) }
                    })
                })
            }
            gson.toJson(context)
        }

        private fun EditorTab.toIdeFileJsonObject(active: Boolean): JsonObject {
            val editor = editorState.editor.get()
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

        private fun result(id: String, result: JsonObject): String =
            JsonObject().apply { addProperty("jsonrpc", "2.0"); add("id", JsonParser.parseString(id)); add("result", result) }.let { gson.toJson(it) }

        private fun callToolTextResult(id: String, text: String): String {
            val content = JsonArray()
            if (text.isNotEmpty()) content.add(JsonObject().apply { addProperty("type", "text"); addProperty("text", text) })
            return result(id, JsonObject().apply { add("content", content) })
        }

        private fun callToolEmptyResult(id: String): String =
            result(id, JsonObject().apply { add("content", JsonArray()) })

        private fun error(id: String?, code: Int, message: String): String =
            JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                add("id", if (id == null || id == "null") JsonNull.INSTANCE else JsonParser.parseString(id))
                add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
            }.let { gson.toJson(it) }

        private fun json(status: Response.Status, body: String): Response =
            newFixedLengthResponse(status, "application/json", body).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "no-store")
            }
    }
}
