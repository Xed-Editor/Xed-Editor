package com.rk.ai

import com.rk.exec.ShellUtils
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.terminal.setupTerminalFiles

object GeminiCli {
    suspend fun prompt(prompt: String, workingDir: String? = null, timeoutSeconds: Long = 180): ShellUtils.Result {
        setupTerminalFiles()
        return ShellUtils.runUbuntu(
            workingDir = workingDir,
            "/bin/bash",
            localBinDir().child("gemini-cli").absolutePath,
            "-p",
            prompt,
            timeoutSeconds = timeoutSeconds,
        )
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
