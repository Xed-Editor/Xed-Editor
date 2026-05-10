package com.rk.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.activities.main.MainViewModel
import com.rk.tabs.editor.EditorTab
import fi.iki.elonen.NanoHTTPD
import com.rk.utils.getTempDir
import java.io.File
import java.security.SecureRandom
import java.util.UUID
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
        NanoHTTPD(0) {
        private val sessionId = UUID.randomUUID().toString()

        fun info(): Info = Info(url = "http://127.0.0.1:$listeningPort", port = listeningPort, token = token, workspacePath)

        fun writeDiscoveryFile() {
            runCatching {
                val dir = File(getTempDir(), "gemini/ide")
                dir.mkdirs()
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
            }
        }

        override fun serve(session: IHTTPSession): Response {
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
                return json(Response.Status.OK, "")
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

        private fun toolsListResult(id: String): String {
            val tools = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("name", "openDiff")
                    addProperty("description", "Open/apply a proposed file diff in Xed-Editor")
                    add("inputSchema", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            add("filePath", JsonObject().apply { addProperty("type", "string") })
                            add("newContent", JsonObject().apply { addProperty("type", "string") })
                        })
                        add("required", JsonArray().apply { add("filePath"); add("newContent") })
                    })
                })
                add(JsonObject().apply {
                    addProperty("name", "closeDiff")
                    addProperty("description", "Close a diff and return final content")
                    add("inputSchema", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            add("filePath", JsonObject().apply { addProperty("type", "string") })
                        })
                        add("required", JsonArray().apply { add("filePath") })
                    })
                })
                add(JsonObject().apply {
                    addProperty("name", "readFile")
                    addProperty("description", "Read the content of a file (prefers open editor content)")
                    add("inputSchema", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            add("filePath", JsonObject().apply { addProperty("type", "string") })
                        })
                        add("required", JsonArray().apply { add("filePath") })
                    })
                })
                add(JsonObject().apply {
                    addProperty("name", "listFiles")
                    addProperty("description", "List files in a directory")
                    add("inputSchema", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            add("directoryPath", JsonObject().apply { addProperty("type", "string") })
                        })
                        add("required", JsonArray().apply { add("directoryPath") })
                    })
                })
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
                    val file = if (File(filePath).isAbsolute) File(filePath) else File(workspacePath, filePath)
                    file.apply {
                        parentFile?.mkdirs()
                        writeText(newContent)
                    }
                    refreshEditors()
                    callToolTextResult(id, "")
                }
                "closeDiff" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    val file = if (File(filePath).isAbsolute) File(filePath) else File(workspacePath, filePath)
                    val content = runCatching { file.readText() }.getOrDefault("")
                    callToolTextResult(id, gson.toJson(JsonObject().apply { addProperty("content", content) }))
                }
                "readFile" -> {
                    val filePath = args.get("filePath")?.asString.orEmpty()
                    if (filePath.isBlank()) return error(id, -32602, "filePath required")
                    val file = if (File(filePath).isAbsolute) File(filePath) else File(workspacePath, filePath)
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
                    val dir = if (File(dirPath).isAbsolute) File(dirPath) else File(workspacePath, dirPath)
                    val files = dir.listFiles()?.joinToString("\n") { it.name + if (it.isDirectory) "/" else "" }.orEmpty()
                    callToolTextResult(id, files)
                }
                else -> error(id, -32601, "unknown tool: $name")
            }
        }

        private fun refreshEditors() {
            viewModel.tabs.filterIsInstance<EditorTab>().forEach { it.refresh() }
        }

        private fun isAuthorized(session: IHTTPSession): Boolean {
            val queryToken = session.parameters["token"]?.firstOrNull()
            val headerToken = session.headers["authorization"]?.removePrefix("Bearer ")
            return queryToken == token || headerToken == token
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
