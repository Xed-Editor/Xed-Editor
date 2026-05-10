package com.rk.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.activities.main.MainViewModel
import com.rk.exec.ShellUtils
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
                    addProperty("workspacePath", workspacePath)
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
                "/mcp" -> serveMcp(session)
                else -> json(Response.Status.NOT_FOUND, error(null, -32601, "not_found"))
            }
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

        private fun toolSchema(
            name: String,
            description: String,
            required: List<String>,
            properties: Map<String, String>,
        ): JsonObject =
            JsonObject().apply {
                addProperty("name", name)
                addProperty("description", description)
                add("inputSchema", JsonObject().apply {
                    addProperty("type", "object")
                    add("properties", JsonObject().apply {
                        properties.forEach { (propertyName, type) ->
                            add(propertyName, JsonObject().apply { addProperty("type", type) })
                        }
                    })
                    add("required", JsonArray().apply { required.forEach { add(it) } })
                })
            }

        private fun toolsListResult(id: String): String {
            val tools = JsonArray().apply {
                add(toolSchema("openDiff", "Open a proposed file replacement in Xed-Editor for user review before writing", listOf("filePath", "newContent"), mapOf("filePath" to "string", "newContent" to "string")))
                add(toolSchema("closeDiff", "Close a diff and return final file content", listOf("filePath"), mapOf("filePath" to "string")))
                add(toolSchema("readFile", "Read the content of a file (prefers open editor content)", listOf("filePath"), mapOf("filePath" to "string")))
                add(toolSchema("listFiles", "List files in a directory. Supports recursive and maxFiles.", listOf("directoryPath"), mapOf("directoryPath" to "string", "recursive" to "boolean", "maxFiles" to "number")))
                add(toolSchema("getOpenFiles", "Return currently open editor files", emptyList(), emptyMap()))
                add(toolSchema("getActiveFile", "Return active editor file, cursor, selection, and content", emptyList(), emptyMap()))
                add(toolSchema("getSelection", "Return selected text in the active editor", emptyList(), emptyMap()))
                add(toolSchema("replaceSelection", "Replace selected text in the active editor after user review", listOf("newContent"), mapOf("newContent" to "string")))
                add(toolSchema("insertAtCursor", "Insert text at the active editor cursor after user review", listOf("newContent"), mapOf("newContent" to "string")))
                add(toolSchema("writeFile", "Write a workspace file and immediately refresh the matching open Xed editor tab", listOf("filePath", "content"), mapOf("filePath" to "string", "content" to "string")))
                add(toolSchema("saveOpenFiles", "Save all dirty open Xed editor tabs to disk before reading/editing files", emptyList(), emptyMap()))
                add(toolSchema("refreshOpenEditors", "Refresh all non-dirty open Xed editor tabs from disk after file edits", emptyList(), emptyMap()))
                add(toolSchema("showMessage", "Show a short message in Xed", listOf("message"), mapOf("message" to "string")))
                add(toolSchema("runCommand", "Run a shell command in the workspace and return stdout/stderr", listOf("command"), mapOf("command" to "string", "timeoutSeconds" to "number")))
                add(toolSchema("refreshFile", "Refresh an open editor tab from disk", listOf("filePath"), mapOf("filePath" to "string")))
            }
            return result(id, JsonObject().apply { add("tools", tools) })
        }

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
                    val oldContent = runCatching { file.readText() }.getOrDefault("")
                    val shown = showPendingPatch(file.absolutePath, oldContent, newContent) {
                        file.parentFile?.mkdirs()
                        file.writeText(newContent)
                        refreshEditors()
                        sendMcpNotification("ide/diffAccepted", JsonObject().apply {
                            addProperty("filePath", file.absolutePath)
                            addProperty("content", newContent)
                        })
                    }
                    if (shown) callToolEmptyResult(id) else callToolErrorResult(id, "No open editor tab is available for review; diff was not applied.")
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
                        val start = if (hasSelection) editor.cursorRange.startIndex else 0
                        val end = if (hasSelection) editor.cursorRange.endIndex else editor.text.toString().length
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
                    val tab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == file.absolutePath }
                    if (tab?.editorState?.isDirty != true) tab?.refresh()
                    callToolTextResult(id, if (tab != null) "refreshed" else "file is not open")
                }
                else -> error(id, -32601, "unknown tool: $name")
            }
        }

        private fun resolveWorkspacePath(path: String): File? {
            val root = runCatching { File(workspacePath).canonicalFile }.getOrNull() ?: return null
            val candidate = if (path.isBlank()) root else if (File(path).isAbsolute) File(path) else File(root, path)
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
            return canonical.takeIf { it.path == root.path || it.path.startsWith(root.path + File.separator) }
        }

        private fun listWorkspaceFiles(dir: File, recursive: Boolean, maxFiles: Int): String {
            if (!dir.exists() || !dir.isDirectory) return ""
            val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules")
            val root = runCatching { File(workspacePath).canonicalFile }.getOrNull() ?: return ""
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
                    ?: return@runBlocking false
                tab.editorState.pendingGeminiPatch =
                    GeminiEditorPatch(
                        title = "Review Gemini file change",
                        filePath = filePath,
                        oldText = oldContent,
                        newText = newContent,
                        apply = {
                            apply()
                            viewModel.tabs.filterIsInstance<EditorTab>().find { it.file.getAbsolutePath() == filePath }?.editorState?.showGeminiAssistant = true
                        },
                        reject = {
                            sendMcpNotification("ide/diffRejected", JsonObject().apply { addProperty("filePath", filePath) })
                        },
                    )
                true
            }

        private fun refreshEditors(onlyClean: Boolean = false) {
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

        private fun callToolErrorResult(id: String, message: String): String =
            result(id, JsonObject().apply {
                addProperty("isError", true)
                add("content", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", message)
                    })
                })
            })

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
