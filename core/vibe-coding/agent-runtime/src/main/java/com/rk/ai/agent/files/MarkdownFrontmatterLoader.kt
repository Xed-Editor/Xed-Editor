package com.rk.ai.agent.files

import android.content.Context
import android.util.Log
import java.io.File

open class MarkdownFrontmatterLoader<T>(
    private val subDir: String,
    private val tag: String,
    private val builder: (id: String, frontmatter: Map<String, String>, body: String, file: File) -> T,
    private val fallback: (id: String, body: String, file: File) -> T? = { _, _, _ -> null },
) {
    fun getDir(context: Context): File {
        val dir = context.filesDir.resolve(subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun list(context: Context): List<T> {
        val dir = getDir(context)
        return dir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { file -> parseFile(file) }
            ?: emptyList()
    }

    fun get(context: Context, name: String): T? {
        val file = getDir(context).resolve("$name.md")
        if (!file.exists()) return null
        return parseFile(file)
    }

    fun save(context: Context, name: String, content: String): T? {
        val dir = getDir(context)
        dir.mkdirs()
        val file = dir.resolve("$name.md")
        file.writeText(content)
        return parseFile(file)
    }

    fun delete(context: Context, name: String): Boolean {
        val file = getDir(context).resolve("$name.md")
        return file.delete()
    }

    open fun parseFile(file: File): T? {
        return try {
            val content = file.readText()
            val id = file.nameWithoutExtension
            val (frontmatter, body) = FrontmatterParser.parseRaw(content)

            if (frontmatter.isEmpty() && !content.startsWith("---")) {
                return fallback(id, body, file) ?: builder(id, frontmatter, body, file)
            }

            builder(id, frontmatter, body, file)
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse file: ${file.name}", e)
            null
        }
    }
}
