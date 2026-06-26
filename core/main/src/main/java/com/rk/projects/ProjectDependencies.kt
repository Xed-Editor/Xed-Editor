package com.rk.projects

import com.rk.exec.ShellUtils
import com.rk.exec.TerminalCommand

/**
 * Detects and installs the toolchain a freshly created project needs.
 *
 * Detection runs headlessly in the Ubuntu sandbox via [ShellUtils.runUbuntu] (`command -v <bin>`).
 * Installation is done in the visible terminal (apt-get) so the user can watch progress, since it
 * downloads packages and can take a while. The sandbox runs as fake-root under proot, so apt needs
 * no sudo.
 */
object ProjectDependencies {

    /**
     * A required tool.
     *
     * @param name human readable name shown in the prompt.
     * @param checkBin executable used to detect presence (`command -v`).
     * @param aptPackages apt packages that provide it.
     */
    data class Tool(val name: String, val checkBin: String, val aptPackages: List<String>)

    /** Tools required by the given project, or empty for templates that need nothing. */
    fun requiredTools(config: ProjectConfig): List<Tool> =
        when (config.template) {
            ProjectTemplate.NONE -> emptyList()
            ProjectTemplate.WEB -> emptyList()
            ProjectTemplate.PYTHON3 ->
                listOf(Tool("Python 3", "python3", listOf("python3", "python3-pip", "python3-venv")))
            ProjectTemplate.PYTHON ->
                listOf(Tool("Python", "python3", listOf("python3", "python3-pip", "python3-venv")))
            ProjectTemplate.NODEJS -> listOf(Tool("Node.js & npm", "node", listOf("nodejs", "npm")))
            ProjectTemplate.MINECRAFT_MOD -> listOf(jdkTool(config.jdkVersion))
            ProjectTemplate.ANDROID_COMPOSE -> listOf(jdkTool(config.jdkVersion))
        }

    private fun jdkTool(jdkVersion: String): Tool {
        val v = jdkVersion.ifBlank { "21" }
        return Tool("JDK $v", "javac", listOf("openjdk-$v-jdk"))
    }

    /**
     * Returns the subset of [tools] that are not currently available in the sandbox. Safe to call
     * even if a check fails (a failing check is treated as "missing").
     */
    suspend fun missingTools(tools: List<Tool>): List<Tool> =
        tools.filter { tool ->
            val result =
                ShellUtils.runUbuntu(
                    command = arrayOf("bash", "-lc", "command -v ${tool.checkBin}"),
                    timeoutSeconds = 15L,
                )
            result.timedOut || result.exitCode != 0 || result.output.isBlank()
        }

    /** Builds a single terminal command that installs every missing package via apt. */
    fun installCommand(missing: List<Tool>): TerminalCommand {
        val packages = missing.flatMap { it.aptPackages }.distinct().joinToString(" ")
        val script =
            buildString {
                append("set -e; ")
                append("echo '==> Installing required tools: $packages'; ")
                append("apt-get update -y; ")
                append("apt-get install -y $packages; ")
                append("echo; echo '==> All set. You can close this terminal.'")
            }
        return TerminalCommand(
            sandbox = true,
            exe = "/bin/bash",
            args = arrayOf("-lc", script),
            id = "install_project_deps",
            terminatePreviousSession = false,
        )
    }
}
