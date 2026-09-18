package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterType
import com.rk.file.FileObject
import com.rk.exec.ShellUtils

object BuiltinTools {
    private const val MAX_READ_BYTES = 64 * 1024
    private const val MAX_OUTPUT_CHARS = 32_000

    fun all(): List<AiTool> =
        listOf(
            fileInfo(),
            readFile(),
            writeFile(),
            editFile(),
            listDir(),
            search(),
            runAndroidShell(),
            runUbuntu(),
        ) + FileOpsTools.all() + HttpTools.all()

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
        ) { args ->
            val file = AiWorkspace.resolve(args.string("path") ?: ".")
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
        ) { args ->
            val file = AiWorkspace.resolve(args.requireString("path"))
            if (!file.isFile()) throw IllegalArgumentException("Not a file: ${args.string("path")}")

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
            val start = (args.int("start_line") ?: 1).coerceAtLeast(1)
            val end = (args.int("end_line") ?: lines.size).coerceAtMost(lines.size)
            if (start > end) throw IllegalArgumentException("start_line ($start) is after end_line ($end)")

            lines.subList(start - 1, end).mapIndexed { index, line ->
                "${start + index}\t$line"
            }
                .joinToString("\n")
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
            targetPath = { it.string("path") },
            preview = { args ->
                val path = args.string("path").orEmpty()
                val oldText =
                    runCatching { AiWorkspace.resolve(path).takeIf { it.isFile() }?.readText() }
                        .getOrNull()
                        .orEmpty()
                AiDiff.unified(path, oldText, args.string("content").orEmpty())
            },
        ) { args ->
            val path = args.requireString("path")
            val content = args.requireString("content")
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
            targetPath = { it.string("path") },
            preview = { args ->
                val path = args.string("path").orEmpty()
                val oldString = args.string("old_string")
                val newString = args.string("new_string")
                val original = runCatching { AiWorkspace.resolve(path).readText() }.getOrNull()
                if (original != null && oldString != null && newString != null) {
                    AiDiff.unified(path, original, original.replace(oldString, newString))
                } else {
                    null
                }
            },
        ) { args ->
            val path = args.requireString("path")
            val file = AiWorkspace.resolve(path)
            if (!file.isFile()) throw IllegalArgumentException("Not a file: $path")
            val oldString = args.requireString("old_string")
            val newString = args.requireString("new_string")
            val replaceAll = args.boolean("replace_all") ?: false

            val original = file.readText()
            val occurrences = Regex(Regex.escape(oldString)).findAll(original).count()
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
        ) { args ->
            val requested = args.string("path") ?: "."
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
        ) { args ->
            val query = args.requireString("query")
            val base = AiWorkspace.resolve(args.string("path") ?: ".")
            val limit = (args.int("max_results") ?: 50).coerceIn(1, 500)

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

    private fun runAndroidShell(): AiTool =
        AiTool(
            name = "run_android_shell",
            description =
                "Run a command in the Android shell (sh) inside the workspace root and return its " +
                    "output and exit code. For Linux tooling, use run_ubuntu instead. The ubuntu runs on-device via proot so this can be used for debugging stuff if the ubuntu is broken",
            kind = AiToolKind.Shell,
            parameters =
                listOf(
                    AiToolParameter("command", "Shell command to execute.", ToolParameterType.String),
                    AiToolParameter(
                        "timeout_seconds",
                        "Seconds before the command is killed. Defaults to 60.",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
        ) { args ->
            val command = args.requireString("command")
            val timeout = (args.int("timeout_seconds") ?: 60).coerceIn(1, 600).toLong()

            val workingDir = AiWorkspace.nativeRootPath()
            if (workingDir == null) {
                shellUnavailable("run_android_shell")
            } else {
                formatShellResult(
                    ShellUtils.run(
                        command = arrayOf("sh", "-c", command),
                        timeoutSeconds = timeout,
                        workingDir = workingDir,
                    ),
                    timeout,
                )
            }
        }

    private fun runUbuntu(): AiTool =
        AiTool(
            name = "run_ubuntu",
            description =
                "Run a shell command as root inside the Ubuntu (proot) environment. Use this for " +
                    "Linux tooling such as apt, node, python, git or native build tools. Android " +
                    "storage is bind-mounted into Ubuntu, so workspace paths work unchanged.",
            kind = AiToolKind.Shell,
            parameters =
                listOf(
                    AiToolParameter("command", "Shell command to run inside Ubuntu.", ToolParameterType.String),
                    AiToolParameter(
                        "working_dir",
                        "Directory inside Ubuntu to start in. Defaults to the AI workspace root.",
                        ToolParameterType.String,
                        required = false,
                    ),
                    AiToolParameter(
                        "timeout_seconds",
                        "Seconds before the command is killed. Defaults to 120.",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
        ) { args ->
            val command = args.requireString("command")
            val timeout = (args.int("timeout_seconds") ?: 120).coerceIn(1, 900).toLong()
            val workingDir = args.string("working_dir") ?: AiWorkspace.nativeRootPath()

            try {
                formatShellResult(
                    ShellUtils.runUbuntu(
                        workingDir = workingDir,
                        command = arrayOf("bash", "-c", command),
                        timeoutSeconds = timeout,
                    ),
                    timeout,
                )
            } catch (e: NoSuchFileException) {
                "Ubuntu is not installed. Install it from Terminal settings, then try again."
            }
        }

    private fun shellUnavailable(tool: String): String {
        val root = AiWorkspace.root()
        return "$tool cannot run: the workspace root is a ${root.aiKind()} file object with no Unix " +
            "path (location: ${root.getAbsolutePath()}). Use the file tools — read_file, write_file, " +
            "edit_file, list_dir, search, file_info — instead."
    }

    internal fun formatShellResult(result: ShellUtils.Result, timeoutSeconds: Long): String {
        val builder = StringBuilder()
        if (result.timedOut) {
            builder.appendLine("Timed out after ${timeoutSeconds}s")
        }
        builder.appendLine("exit=${result.exitCode}")
        if (result.output.isNotBlank()) {
            builder.appendLine(result.output)
        }
        if (result.error.isNotBlank()) {
            builder.appendLine("stderr:")
            builder.appendLine(result.error)
        }
        return builder.toString().trim().take(MAX_OUTPUT_CHARS)
    }

}
