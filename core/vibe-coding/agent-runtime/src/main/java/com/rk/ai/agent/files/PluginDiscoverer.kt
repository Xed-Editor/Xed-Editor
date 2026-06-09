package com.rk.ai.agent.files

import android.content.Context
import android.util.Log
import java.io.File

data class PluginManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val minApiVersion: String? = null,
    val author: String? = null,
    val tools: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val agents: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val hooks: List<String> = emptyList(),
    val enabled: Boolean = true,
    val pluginDir: File,
) {
    val manifestFile: File get() = pluginDir.resolve("plugin.json")
    val isBuiltin: Boolean get() = id.startsWith("builtin-")
}

data class PluginDiscoveryResult(
    val loaded: List<PluginManifest>,
    val errors: List<String>,
)

object PluginDiscoverer {
    private const val TAG = "PluginDiscoverer"
    private const val PLUGINS_DIR = "plugins"
    private const val MANIFEST_FILE = "plugin.json"

    fun getPluginsDir(context: Context): File {
        val dir = context.filesDir.resolve(PLUGINS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun discoverAll(context: Context): PluginDiscoveryResult {
        val loaded = mutableListOf<PluginManifest>()
        val errors = mutableListOf<String>()

        val dir = getPluginsDir(context)
        val pluginDirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        for (pluginDir in pluginDirs) {
            when (val result = loadManifest(pluginDir)) {
                is PluginManifest -> loaded.add(result)
                else -> errors.add("Failed to load plugin from '${pluginDir.name}': $result")
            }
        }

        return PluginDiscoveryResult(loaded, errors)
    }

    fun loadManifest(pluginDir: File): Any {
        val manifestFile = File(pluginDir, MANIFEST_FILE)
        if (!manifestFile.exists()) return "manifest file not found"

        return try {
            val content = manifestFile.readText()
            val (frontmatter, _) = FrontmatterParser.parseRaw(content)

            val id = frontmatter["id"] ?: pluginDir.name
            val name = frontmatter["name"] ?: id.replaceFirstChar { it.uppercase() }
            val description = frontmatter["description"] ?: ""
            val version = frontmatter["version"] ?: "1.0.0"
            val minApiVersion = frontmatter["min-api-version"]
            val author = frontmatter["author"]

            val tools = parseEntries(content, "tools")
            val commands = parseEntries(content, "commands")
            val agents = parseEntries(content, "agents")
            val skills = parseEntries(content, "skills")
            val hooks = parseEntries(content, "hooks")

            PluginManifest(
                id = id,
                name = name,
                description = description,
                version = version,
                minApiVersion = minApiVersion,
                author = author,
                tools = tools,
                commands = commands,
                agents = agents,
                skills = skills,
                hooks = hooks,
                pluginDir = pluginDir,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse manifest in ${pluginDir.name}", e)
            "parse error: ${e.message}"
        }
    }

    fun createPlugin(context: Context, id: String, name: String, description: String): PluginManifest? {
        val dir = File(getPluginsDir(context), id)
        if (dir.exists()) return null
        dir.mkdirs()

        val manifestContent = buildString {
            appendLine("---")
            appendLine("id: $id")
            appendLine("name: $name")
            appendLine("description: $description")
            appendLine("version: 1.0.0")
            appendLine("---")
            appendLine()
            appendLine("# $name")
            appendLine()
            appendLine(description)
        }

        File(dir, MANIFEST_FILE).writeText(manifestContent)
        return loadManifest(dir) as? PluginManifest
    }

    fun deletePlugin(context: Context, pluginId: String): Boolean {
        val dir = File(getPluginsDir(context), pluginId)
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    private fun parseEntries(content: String, section: String): List<String> {
        val pattern = Regex("""$section:\s*\[([^\]]*)\]""")
        val match = pattern.find(content)
            ?: return emptyList()
        return match.groupValues[1]
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    fun enablePlugin(context: Context, pluginId: String): Boolean {
        val plugin = discoverAll(context).loaded.find { it.id == pluginId } ?: return false
        return saveEnabledState(plugin, true)
    }

    fun disablePlugin(context: Context, pluginId: String): Boolean {
        val plugin = discoverAll(context).loaded.find { it.id == pluginId } ?: return false
        return saveEnabledState(plugin, false)
    }

    private fun saveEnabledState(manifest: PluginManifest, enabled: Boolean): Boolean {
        return try {
            val content = manifest.manifestFile.readText()
            val (frontmatter, body) = FrontmatterParser.parseRaw(content)
            val newFrontmatter = frontmatter.toMutableMap().apply {
                put("enabled", enabled.toString())
            }
            val newContent = buildString {
                appendLine("---")
                newFrontmatter.forEach { (k, v) -> appendLine("$k: $v") }
                appendLine("---")
                appendLine(body)
            }
            manifest.manifestFile.writeText(newContent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set enabled state for ${manifest.id}", e)
            false
        }
    }
}
