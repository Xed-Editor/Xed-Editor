package com.rk.ai.bridge.server

import android.util.Log
import com.rk.ai.service.IdeService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import java.io.File

object McpResourceProvider {
    private const val TAG = "McpResourceProvider"

    fun registerResources(
        server: Server,
        workspacePaths: List<String>,
        ideServiceProvider: () -> IdeService,
    ) {
        server.addResource(
            uri = "xed://workspace/tree",
            name = "workspace_tree",
            description = "Directory tree of the current workspace(s), up to 3 levels deep",
            mimeType = "application/json",
        ) { _ ->
            val tree = buildWorkspaceTree(workspacePaths)
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = tree,
                        uri = "xed://workspace/tree",
                        mimeType = "application/json",
                    ),
                ),
            )
        }

        server.addResource(
            uri = "xed://diagnostics",
            name = "diagnostics",
            description = "Current LSP diagnostics (errors, warnings) for open files",
            mimeType = "application/json",
        ) { _ ->
            val diagnostics = buildDiagnosticsJson(ideServiceProvider())
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = diagnostics,
                        uri = "xed://diagnostics",
                        mimeType = "application/json",
                    ),
                ),
            )
        }

        server.addResourceTemplate(
            uriTemplate = "xed://file/{path}",
            name = "file_contents",
            description = "Read the contents of a file in the workspace",
            mimeType = "text/plain",
        ) { _, params ->
            val path = params["path"] ?: return@addResourceTemplate ReadResourceResult(
                contents = listOf(TextResourceContents(text = "Missing path parameter", uri = "xed://file/?")),
            )
            val ideService = ideServiceProvider()
            val content = readWorkspaceFile(path, workspacePaths, ideService)
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = content,
                        uri = "xed://file/$path",
                        mimeType = guessMimeType(path),
                    ),
                ),
            )
        }

        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.d(TAG, "Registered MCP resources: workspace_tree, diagnostics, file template")
        }
    }

    private fun buildWorkspaceTree(workspacePaths: List<String>): String {
        val tree = workspacePaths.map { ws ->
            val root = File(ws)
            if (!root.exists()) return@map mapOf("path" to ws, "error" to "not found")
            mapOf(
                "path" to ws,
                "name" to root.name,
                "children" to buildDirTree(root, maxDepth = 3, currentDepth = 0),
            )
        }
        val elements = tree.map { node ->
            kotlinx.serialization.json.buildJsonObject {
                put("path", JsonPrimitive(node["path"] as String))
                put("name", JsonPrimitive(node["name"] as String))
                @Suppress("UNCHECKED_CAST")
                val children = node["children"] as? List<Map<String, Any>> ?: emptyList()
                put("children", serializeTreeNodes(children))
            }
        }
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            kotlinx.serialization.json.JsonArray(elements),
        )
    }

    private fun buildDirTree(dir: File, maxDepth: Int, currentDepth: Int): List<Map<String, Any>> {
        if (currentDepth >= maxDepth) return emptyList()
        return try {
            dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
                ?.take(100)
                ?.map { f ->
                    val node = mutableMapOf<String, Any>(
                        "name" to f.name,
                        "path" to f.absolutePath,
                    )
                    if (f.isDirectory) {
                        node["type"] = "directory"
                        val children = buildDirTree(f, maxDepth, currentDepth + 1)
                        if (children.isNotEmpty()) node["children"] = children
                    } else {
                        node["type"] = "file"
                        node["size"] = f.length()
                    }
                    node
                } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeTreeNodes(nodes: List<Map<String, Any>>): kotlinx.serialization.json.JsonArray {
        return kotlinx.serialization.json.JsonArray(
            nodes.map { node ->
                kotlinx.serialization.json.buildJsonObject {
                    put("name", JsonPrimitive(node["name"] as String))
                    put("path", JsonPrimitive(node["path"] as String))
                    put("type", JsonPrimitive(node["type"] as String))
                    if (node.containsKey("size")) {
                        put("size", JsonPrimitive(node["size"] as Long))
                    }
                    @Suppress("UNCHECKED_CAST")
                    val children = node["children"] as? List<Map<String, Any>>
                    if (children != null && children.isNotEmpty()) {
                        put("children", serializeTreeNodes(children))
                    }
                }
            },
        )
    }

    private fun readWorkspaceFile(path: String, workspacePaths: List<String>, ideService: IdeService): String {
        val resolved = ideService.resolvePath(path)
        if (resolved != null && resolved.exists() && resolved.isFile) {
            return try {
                resolved.readText().take(1_000_000)
            } catch (e: Exception) {
                "Error reading file: ${e.message}"
            }
        }
        for (ws in workspacePaths) {
            val file = File(ws, path)
            if (file.exists() && file.isFile) {
                return try {
                    file.readText().take(1_000_000)
                } catch (e: Exception) {
                    "Error reading file: ${e.message}"
                }
            }
        }
        return "File not found: $path"
    }

    private fun buildDiagnosticsJson(ideService: IdeService): String {
        return try {
            val openFiles = runBlocking { ideService.getOpenFiles() }
            val results = mutableListOf<String>()
            for (fileObj in openFiles) {
                val filePath = fileObj.get("path")?.asString ?: continue
                val diags = runBlocking { ideService.getDiagnostics(filePath) }
                if (diags.size() > 0) {
                    results.add("{\"file\":\"$filePath\",\"diagnostics\":$diags}")
                }
            }
            "[${results.joinToString(",")}]"
        } catch (_: Exception) {
            "[]"
        }
    }

    private fun guessMimeType(path: String): String {
        return when {
            path.endsWith(".kt") || path.endsWith(".kts") -> "text/x-kotlin"
            path.endsWith(".java") -> "text/x-java"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".xml") -> "text/xml"
            path.endsWith(".yaml") || path.endsWith(".yml") -> "text/yaml"
            path.endsWith(".md") -> "text/markdown"
            path.endsWith(".txt") -> "text/plain"
            path.endsWith(".gradle") -> "text/x-gradle"
            else -> "text/plain"
        }
    }
}
