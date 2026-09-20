package com.rk.ai.tools

import ai.koog.agents.core.tools.ToolParameterType
import com.rk.exec.ShellUtils

object ShellTools {
    private const val MAX_OUTPUT_CHARS = 32_000
    private const val DEFAULT_ANDROID_TIMEOUT = 60
    private const val DEFAULT_UBUNTU_TIMEOUT = 120

    fun all(): List<AiTool> = listOf(runAndroidShell(), runUbuntu())

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
                        "Seconds before the command is killed. Defaults to $DEFAULT_ANDROID_TIMEOUT.",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Run shell",
                        args.displayArg("command"),
                        listOfNotNull(
                            toolLargeBlock("Command", args.displayArg("command")),
                            toolField("Timeout", args.displayArg("timeout_seconds")?.plus("s")),
                        ),
                    )
                },
        ) { args ->
            val command = args.requireArg("command")
            val timeout = (args.argInt("timeout_seconds") ?: DEFAULT_ANDROID_TIMEOUT).coerceIn(1, 600).toLong()

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
                        "Seconds before the command is killed. Defaults to $DEFAULT_UBUNTU_TIMEOUT.",
                        ToolParameterType.Integer,
                        required = false,
                    ),
                ),
            presenter =
                AiToolPresenter { args ->
                    ToolCallView(
                        "Run in Ubuntu",
                        args.displayArg("command"),
                        listOfNotNull(
                            toolLargeBlock("Command", args.displayArg("command")),
                            toolField("Working dir", args.displayArg("working_dir")),
                            toolField("Timeout", args.displayArg("timeout_seconds")?.plus("s")),
                        ),
                    )
                },
        ) { args ->
            val command = args.requireArg("command")
            val timeout = (args.argInt("timeout_seconds") ?: DEFAULT_UBUNTU_TIMEOUT).coerceIn(1, 900).toLong()
            val workingDir = args.arg("working_dir") ?: AiWorkspace.nativeRootPath()

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

    fun formatShellResult(result: ShellUtils.Result, timeoutSeconds: Long): String {
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
