package com.rk.ai

import com.rk.exec.ShellUtils
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.terminal.setupTerminalFiles

object GeminiCli {
    suspend fun prompt(
        prompt: String,
        workingDir: String? = null,
        projectDir: String? = workingDir,
        timeoutSeconds: Long = 180,
    ): ShellUtils.Result {
        return runGemini(
            args = commonArgs(projectDir) + listOf("--approval-mode=default", "-p", prompt),
            workingDir = workingDir,
            timeoutSeconds = timeoutSeconds,
        )
    }

    suspend fun agent(
        prompt: String,
        workingDir: String? = null,
        projectDir: String? = workingDir,
        timeoutSeconds: Long = 600,
    ): ShellUtils.Result {
        return runGemini(
            args = commonArgs(projectDir) + listOf("--approval-mode=auto_edit", "-p", prompt),
            workingDir = workingDir,
            timeoutSeconds = timeoutSeconds,
        )
    }

    private fun commonArgs(projectDir: String?): List<String> {
        return buildList {
            add("--skip-trust")
            add("--output-format")
            add("text")
            projectDir?.takeIf { it.isNotBlank() }?.let {
                add("--include-directories")
                add(it)
            }
        }
    }

    private suspend fun runGemini(args: List<String>, workingDir: String?, timeoutSeconds: Long): ShellUtils.Result {
        setupTerminalFiles()
        val command =
            arrayOf(
                "/bin/bash",
                localBinDir().child("gemini-cli-headless").absolutePath,
                *args.toTypedArray(),
            )
        return ShellUtils.runUbuntu(workingDir, *command, timeoutSeconds = timeoutSeconds)
    }

    suspend fun workingDirFor(file: FileObject, projectRoot: FileObject?): String {
        return projectRoot?.getAbsolutePath()
            ?: (file.getParentFile() as? FileWrapper)?.getAbsolutePath()
            ?: file.getAbsolutePath()
    }

    fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return text.trimEnd()
        val lines = trimmed.lines().toMutableList()
        if (lines.isNotEmpty() && lines.first().startsWith("```")) lines.removeAt(0)
        if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeAt(lines.lastIndex)
        return lines.joinToString("\n").trimEnd()
    }
}
