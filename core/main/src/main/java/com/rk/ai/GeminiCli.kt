package com.rk.ai

import com.rk.exec.ShellUtils
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.terminal.setupTerminalFiles

object GeminiCli {
    suspend fun prompt(prompt: String, workingDir: String? = null, timeoutSeconds: Long = 180): ShellUtils.Result {
        return runGemini(listOf("-p", prompt), workingDir, timeoutSeconds)
    }

    suspend fun agent(prompt: String, workingDir: String? = null, timeoutSeconds: Long = 300): ShellUtils.Result {
        return runGemini(listOf("--approval-mode=auto_edit", "-p", prompt), workingDir, timeoutSeconds)
    }

    private suspend fun runGemini(args: List<String>, workingDir: String?, timeoutSeconds: Long): ShellUtils.Result {
        setupTerminalFiles()
        val command =
            arrayOf("/bin/bash", localBinDir().child("gemini-cli-headless").absolutePath, *args.toTypedArray())
        return ShellUtils.runUbuntu(workingDir, *command, timeoutSeconds = timeoutSeconds)
    }

    fun workingDirFor(file: FileObject): String? = (file as? FileWrapper)?.getParentFile()?.getAbsolutePath()

    fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return text.trimEnd()
        val lines = trimmed.lines().toMutableList()
        if (lines.isNotEmpty() && lines.first().startsWith("```")) lines.removeAt(0)
        if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeAt(lines.lastIndex)
        return lines.joinToString("\n").trimEnd()
    }
}
