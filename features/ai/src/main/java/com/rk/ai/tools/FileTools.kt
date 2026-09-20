package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterType
import com.rk.file.FileObject

internal object FileTools {
    private const val MAX_READ_BYTES = 64 * 1024

    fun all(): List<AiTool> = listOf(fileInfo(), readFile(), writeFile(), editFile(), listDir(), search())

    private fun fileInfo(): AiTool =
        AiTool(
            name = "file_info",
            description =
                "Describe a path before using it: what kind of file object it is (local file, " +
                    "Android document URI, remote URL, archive entry), whether it exists, its size, " +
                    "and whether the shell tools have a Unix path for it. Remote and document " +
                    "objects can be read and written by the file tools but not by the shell tools.",
            kind = AiToolKind.Read,
            parameters =
                listOf(
                    AiToolParameter(
                        "path",
                        "Path relative to the workspace root; use '.' for the workspace root itself.",
                        ToolParameterType.String,
                        required = false,
                    )
                ),
            presenter = AiToolPresenter { args -> ToolCallView("Inspect path", args.displayArg("path") ?: ".", emptyList()) },
        ) { args ->
            val file = AiWorkspace.resolve(args.arg("path") ?: ".")
            val native = file.nativePathOrNull()
            listOfNotNull(
                    "kind: ${file.aiKind()}",
                    "name: ${file.getName()}",
                    "location: ${file.getAbsolutePath()}",
                    "exists: ${file.exists()}",
                    "directory: ${file.isDirectory()}",
                    if (file.isFile()) "size_bytes: ${file.length()}" else null,
                    "readable: ${file.canRead()}",
                    "writable: ${file.canWrite()}",
                    if (native != null) {
                        "shell_path: $native"
                    } else {
                        "shell_path: none — run_android_shell and run_ubuntu cannot reach this " +
                            "${file.aiKind()} object; use the file tools instead"
                    },
                )
                .joinToString("\n")
        }

    private fun readFile(): AiTool =
        AiTool(
            name = "read_file",
            description =
                "Read a UTF-8 text file from the workspace. Optionally restrict to a line range. " +
                    "Returns the file contents with line numbers.",
            kind = AiToolKind.Read,
            parameters =
                listOf(
                    AiToolParameter("path", "Path relative to the workspace root.", ToolParameterType.String),
                    AiToolParameter(
                        "start_line",
                        "First line to return (1-based).",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                    AiToolParameter(
                        "end_line",
                        "Last line to return (inclusive).",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView("Read file", args.displayArg("path"), listOfNotNull(args.lineRange()))
                },
        ) { args ->
            val file = AiWorkspace.resolve(args.requireArg("path"))
            if (!file.isFile()) throw IllegalArgumentException("Not a file: ${args.arg("path")}")

            val length = file.length()
            val text =
                if (length > MAX_READ_BYTES) {
                    file.useInputStream { input ->
                        val buffer = ByteArray(MAX_READ_BYTES)
                        var read = 0
                        while (read < buffer.size) {
                            val count = input.read(buffer, read, buffer.size - read)
                            if (count < 0) break
                            read += count
                        }
                        String(buffer, 0, read, Charsets.UTF_8) +
                            "\n… truncated at $MAX_READ_BYTES bytes (file is $length bytes)"
                    }
                } else {
                    file.readText()
                }

            val lines = text.lines()
            val start = (args.argInt("start_line") ?: 1).coerceAtLeast(1)
            val end = (args.argInt("end_line") ?: lines.size).coerceAtMost(lines.size)
            if (start > end) throw IllegalArgumentException("start_line ($start) is after end_line ($end)")

            lines.subList(start - 1, end).mapIndexed { index, line -> "${start + index}\t$line" }.joinToString("\n")
        }

    private fun writeFile(): AiTool =
        AiTool(
            name = "write_file",
            description =
                "Create or overwrite a workspace file with the given UTF-8 content. " +
                    "Creates parent directories as needed.",
            kind = AiToolKind.Write,
            parameters =
                listOf(
                    AiToolParameter("path", "Path relative to the workspace root.", ToolParameterType.String),
                    AiToolParameter("content", "Full file content to write.", ToolParameterType.String),
                ),
            targetPath = { it.arg("path") },
            preview = { args ->
                val path = args.arg("path").orEmpty()
                val oldText =
                    runCatching { AiWorkspace.resolve(path).takeIf { it.isFile() }?.readText() }
                        .getOrNull()
                        .orEmpty()
                AiDiff.unified(path, oldText, args.arg("content").orEmpty())
            },
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Write file",
                        args.displayArg("path"),
                        listOfNotNull(toolBlock("Content", args.displayArg("content"))),
                    )
                },
        ) { args ->
            val path = args.requireArg("path")
            val content = args.requireArg("content")
            val file = AiWorkspace.resolveForWrite(path)
            file.writeText(content)
            "Wrote ${content.length} characters to ${AiWorkspace.relativize(file)}"
        }

    private fun editFile(): AiTool =
        AiTool(
            name = "edit_file",
            description =
                "Replace an exact string in a workspace file. Fails if 'old_string' is not found " +
                    "or is ambiguous unless replace_all is true.",
            kind = AiToolKind.Write,
            parameters =
                listOf(
                    AiToolParameter("path", "Path relative to the workspace root.", ToolParameterType.String),
                    AiToolParameter("old_string", "Exact text to replace.", ToolParameterType.String),
                    AiToolParameter("new_string", "Replacement text.", ToolParameterType.String),
                    AiToolParameter(
                        "replace_all",
                        "Replace every occurrence instead of requiring a unique match.",
                        ToolParameterType.Boolean,
                        required = false,
                    ),
                ),
            targetPath = { it.arg("path") },
            preview = { args ->
                val path = args.arg("path").orEmpty()
                val oldString = args.arg("old_string")
                val newString = args.arg("new_string")
                val original = runCatching { AiWorkspace.resolve(path).readText() }.getOrNull()
                if (original != null && oldString != null && newString != null) {
                    AiDiff.unified(path, original, original.replace(oldString, newString))
                } else {
                    null
                }
            },
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Edit file",
                        args.displayArg("path"),
                        listOfNotNull(
                            toolBlock("Find", args.displayArg("old_string")),
                            toolBlock("Replace with", args.displayArg("new_string")),
                            if (args.displayArg("replace_all").toBoolean()) {
                                ToolBodyPart.Field("Replace all", "yes")
                            } else {
                                null
                            },
                        ),
                    )
                },
        ) { args ->
            val path = args.requireArg("path")
            val file = AiWorkspace.resolve(path)
            if (!file.isFile()) throw IllegalArgumentException("Not a file: $path")
            val oldString = args.requireArg("old_string")
            val newString = args.requireArg("new_string")
            val replaceAll = args.argBoolean("replace_all") ?: false

            val original = file.readText()
            val occurrences = original.occurrencesOf(oldString)
            if (occurrences == 0) throw IllegalArgumentException("old_string was not found in the file")
            if (occurrences > 1 && !replaceAll) {
                throw IllegalArgumentException(
                    "old_string occurs $occurrences times; pass replace_all=true or include more context"
                )
            }

            val updated =
                if (replaceAll) original.replace(oldString, newString) else original.replaceFirst(oldString, newString)
            file.writeText(updated)
            "Edited ${AiWorkspace.relativize(file)} ($occurrences replacement(s))"
        }

    private fun listDir(): AiTool =
        AiTool(
            name = "list_dir",
            description = "List the entries of a workspace directory. Defaults to the workspace root.",
            kind = AiToolKind.Read,
            parameters =
                listOf(
                    AiToolParameter(
                        "path",
                        "Directory path relative to the workspace root. Defaults to '.'.",
                        ToolParameterType.String,
                        required = false,
                    )
                ),
            presenter = AiToolPresenter { args -> ToolCallView("List directory", args.displayArg("path") ?: ".", emptyList()) },
        ) { args ->
            val requested = args.arg("path") ?: "."
            val dir = AiWorkspace.resolve(requested)
            if (!dir.isDirectory()) throw IllegalArgumentException("Not a directory: $requested")

            val entries = dir.listFiles().sortedWith(compareBy({ !it.isDirectory() }, { it.getName() }))
            if (entries.isEmpty()) {
                "(empty directory)"
            } else {
                entries.joinToString("\n") { entry ->
                    val suffix = if (entry.isDirectory()) "/" else ""
                    "${entry.getName()}$suffix"
                }
            }
        }

    private fun search(): AiTool =
        AiTool(
            name = "search",
            description =
                "Search workspace text files for a literal, case-insensitive substring. " +
                    "Returns 'path:line: text' matches.",
            kind = AiToolKind.Read,
            parameters =
                listOf(
                    AiToolParameter("query", "Literal text to look for.", ToolParameterType.String),
                    AiToolParameter(
                        "path",
                        "Directory to search, relative to the workspace root. Defaults to '.'.",
                        ToolParameterType.String,
                        required = false,
                    ),
                    AiToolParameter(
                        "max_results",
                        "Maximum number of matches to return. Defaults to 50.",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Search",
                        args.displayArg("query"),
                        listOfNotNull(
                            toolField("In", args.displayArg("path")),
                            toolField("Max results", args.displayArg("max_results")),
                        ),
                    )
                },
        ) { args ->
            val query = args.requireArg("query")
            val base = AiWorkspace.resolve(args.arg("path") ?: ".")
            val limit = (args.argInt("max_results") ?: 50).coerceIn(1, 500)

            val matches = mutableListOf<String>()
            collectMatches(base, query, limit, matches)

            if (matches.isEmpty()) "No matches for '$query'." else matches.joinToString("\n")
        }

    private suspend fun collectMatches(
        dir: FileObject,
        query: String,
        limit: Int,
        matches: MutableList<String>,
    ) {
        if (matches.size >= limit) return

        val children = runCatching { dir.listFiles() }.getOrDefault(emptyList())
        for (child in children.sortedBy { it.getName() }) {
            if (matches.size >= limit) return

            if (child.isDirectory()) {
                if (child.getName() !in AI_IGNORED_DIRS) collectMatches(child, query, limit, matches)
                continue
            }

            val size = runCatching { child.length() }.getOrDefault(0L)
            if (size > MAX_READ_BYTES) continue

            val text = runCatching { child.readText() }.getOrNull() ?: continue
            text.lineSequence().forEachIndexed { index, line ->
                if (matches.size < limit && line.contains(query, ignoreCase = true)) {
                    matches.add("${AiWorkspace.relativize(child)}:${index + 1}: ${line.trim()}")
                }
            }
        }
    }
}

/** Counts non-overlapping occurrences without building a regex. */
private fun String.occurrencesOf(needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var index = indexOf(needle)
    while (index >= 0) {
        count++
        index = indexOf(needle, index + needle.length)
    }
    return count
}
