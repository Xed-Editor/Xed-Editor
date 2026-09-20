package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterType
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import java.io.IOException

internal object FileOpsTools {
    private const val MAX_FIND_RESULTS = 500

    fun all(): List<AiTool> = listOf(makeDir(), copyPath(), movePath(), deletePath(), findFiles())

    private fun makeDir(): AiTool =
        AiTool(
            name = "make_dir",
            description = "Create a directory, including any missing parent directories.",
            kind = AiToolKind.Write,
            parameters =
                listOf(
                    AiToolParameter("path", "Directory path relative to the workspace root.", ToolParameterType.String)
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(strings.ai_tool_create_directory.getString(), args.displayArg("path"), emptyList())
                },
        ) { args ->
            val path = args.requireArg("path")
            val dir = AiWorkspace.createDirectory(path)
            "Directory ready: ${AiWorkspace.relativize(dir)}/"
        }

    private fun copyPath(): AiTool =
        AiTool(
            name = "copy_path",
            description =
                "Copy a file or directory to another workspace path. Directories are copied " +
                    "recursively. Fails if the destination already exists.",
            kind = AiToolKind.Write,
            parameters =
                listOf(
                    AiToolParameter("from", "Source path relative to the workspace root.", ToolParameterType.String),
                    AiToolParameter("to", "Destination path relative to the workspace root.", ToolParameterType.String),
                ),
            presenter = AiToolPresenter { args -> ToolCallView(strings.copy.getString(), args.pairOfPaths(), emptyList()) },
        ) { args ->
            val from = args.requireArg("from")
            val to = args.requireArg("to")
            val source = AiWorkspace.resolve(from)
            val target = createDestination(to, isDirectory = source.isDirectory())
            copyRecursively(source, target)
            "Copied $from -> $to"
        }

    private fun movePath(): AiTool =
        AiTool(
            name = "move_path",
            description =
                "Move or rename a file or directory within the workspace. Implemented as a copy " +
                    "followed by deleting the source, so it also works across directories.",
            kind = AiToolKind.Write,
            parameters =
                listOf(
                    AiToolParameter("from", "Source path relative to the workspace root.", ToolParameterType.String),
                    AiToolParameter("to", "Destination path relative to the workspace root.", ToolParameterType.String),
                ),
            presenter = AiToolPresenter { args -> ToolCallView(strings.ai_tool_move.getString(), args.pairOfPaths(), emptyList()) },
        ) { args ->
            val from = args.requireArg("from")
            val to = args.requireArg("to")
            val source = AiWorkspace.resolve(from)
            val target = createDestination(to, isDirectory = source.isDirectory())

            copyRecursively(source, target)
            if (!source.delete()) throw IOException("Copied to $to but could not remove $from")
            "Moved $from -> $to"
        }

    private fun deletePath(): AiTool =
        AiTool(
            name = "delete_path",
            description = "Delete a file or directory (recursively). This cannot be undone.",
            kind = AiToolKind.Write,
            parameters =
                listOf(
                    AiToolParameter("path", "Path relative to the workspace root.", ToolParameterType.String)
                ),
            presenter = AiToolPresenter { args -> ToolCallView(strings.delete.getString(), args.displayArg("path"), emptyList()) },
        ) { args ->
            val path = args.requireArg("path")
            val target = AiWorkspace.resolve(path)
            require(target.getAbsolutePath() != AiWorkspace.root().getAbsolutePath()) {
                "Refusing to delete the workspace root"
            }
            if (!target.delete()) throw IOException("Failed to delete $path")
            "Deleted $path"
        }

    private fun findFiles(): AiTool =
        AiTool(
            name = "find_files",
            description =
                "Find files by glob pattern, e.g. '*.kt', '**/*.gradle.kts' or 'src/**/Main.kt'. " +
                    "A pattern without '/' matches at any depth. Returns workspace-relative paths.",
            kind = AiToolKind.Read,
            parameters =
                listOf(
                    AiToolParameter("pattern", "Glob pattern to match against the relative path.", ToolParameterType.String),
                    AiToolParameter(
                        "path",
                        "Directory to search, relative to the workspace root. Defaults to '.'.",
                        ToolParameterType.String,
                        required = false,
                    ),
                    AiToolParameter(
                        "max_results",
                        "Maximum number of paths to return. Defaults to 200.",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        strings.ai_tool_find_files.getString(),
                        args.displayArg("pattern"),
                        listOfNotNull(
                            toolField(strings.ai_tool_in.getString(), args.displayArg("path")),
                            toolField(strings.ai_tool_max_results.getString(), args.displayArg("max_results")),
                        ),
                    )
                },
        ) { args ->
            val pattern = args.requireArg("pattern")
            val base = AiWorkspace.resolve(args.arg("path") ?: ".")
            val limit = (args.argInt("max_results") ?: 200).coerceIn(1, MAX_FIND_RESULTS)
            val regex = globToRegex(if (pattern.contains('/')) pattern else "**/$pattern")

            val matches = mutableListOf<String>()
            findMatches(base, regex, limit, matches)

            if (matches.isEmpty()) "No files match '$pattern'." else matches.joinToString("\n")
        }

    private suspend fun createDestination(path: String, isDirectory: Boolean): FileObject {
        val (parent, name) = AiWorkspace.destination(path)
        require(parent.getChild(name) == null) { "Destination already exists: $path" }
        return parent.createChild(createFile = !isDirectory, name = name)
            ?: throw IOException("Cannot create $path")
    }

    private suspend fun copyRecursively(source: FileObject, target: FileObject) {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs()
            for (child in source.listFiles()) {
                val targetChild =
                    target.getChild(child.getName())
                        ?: target.createChild(createFile = !child.isDirectory(), name = child.getName())
                        ?: throw IOException("Cannot create ${child.getName()}")
                copyRecursively(child, targetChild)
            }
        } else {
            source.useInputStream { input ->
                target.getOutputStream(false).use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun findMatches(
        dir: FileObject,
        regex: Regex,
        limit: Int,
        matches: MutableList<String>,
    ) {
        if (matches.size >= limit) return

        val children = runCatching { dir.listFiles() }.getOrDefault(emptyList())
        for (child in children.sortedBy { it.getName() }) {
            if (matches.size >= limit) return

            if (child.isDirectory()) {
                if (child.getName() !in AI_IGNORED_DIRS) findMatches(child, regex, limit, matches)
            } else {
                val relative = AiWorkspace.relativize(child)
                if (regex.matches(relative)) matches.add(relative)
            }
        }
    }

    internal fun globToRegex(pattern: String): Regex {
        val out = StringBuilder("^")
        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            when {
                char == '*' && pattern.getOrNull(index + 2) == '/' -> {
                    out.append("(?:.*/)?")
                    index += 3
                    continue
                }
                char == '*' && pattern.getOrNull(index + 1) == '*' -> {
                    out.append(".*")
                    index += 2
                    continue
                }
                char == '*' -> out.append("[^/]*")
                char == '?' -> out.append("[^/]")
                char in REGEX_SPECIALS -> out.append('\\').append(char)
                else -> out.append(char)
            }
            index++
        }
        out.append('$')
        return Regex(out.toString())
    }

    private const val REGEX_SPECIALS = ".()+|^$@%{}[]\\"
}
