package com.rk.ai

import com.rk.exec.ShellUtils
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.terminal.setupTerminalFiles
import com.rk.utils.getTempDir

object GeminiCli {
    suspend fun prompt(
        prompt: String,
        workingDir: String? = null,
        projectDir: String? = workingDir,
        ideBridge: GeminiBridge.Info? = null,
        timeoutSeconds: Long = 180,
        onOutput: ((String) -> Unit)? = null,
    ): ShellUtils.Result {
        return runGemini(
            args = commonArgs(projectDir) + listOf("--approval-mode=default", "-p", prompt),
            workingDir = workingDir,
            ideBridge = ideBridge,
            timeoutSeconds = timeoutSeconds,
            onOutput = onOutput,
        )
    }

    suspend fun agent(
        prompt: String,
        workingDir: String? = null,
        projectDir: String? = workingDir,
        ideBridge: GeminiBridge.Info? = null,
        timeoutSeconds: Long = 600,
        onOutput: ((String) -> Unit)? = null,
    ): ShellUtils.Result {
        return runGemini(
            args = commonArgs(projectDir) + listOf("--approval-mode=auto_edit", "-p", prompt),
            workingDir = workingDir,
            ideBridge = ideBridge,
            timeoutSeconds = timeoutSeconds,
            onOutput = onOutput,
        )
    }

    fun cleanOutput(text: String): String {
        val ansiRegex = Regex("\\u001B\\[[;\\d]*m")
        val ignoreRegexes = listOf(
            Regex("^(INFO|WARN|ERROR|DEBUG|TRACE)\\s+.*"),
            Regex(".*ClearcutLogger.*"),
            Regex(".*Flush already in progress.*"),
            Regex(".*No GEMINI_API_KEY/GOOGLE_API_KEY.*"),
            Regex(".*No API key found\\. Headless mode.*"),
            Regex(".*Starting Gemini CLI in.*"),
            Regex("^\\s*$")
        )

        return text
            .lineSequence()
            .map { line -> line.replace(ansiRegex, "") }
            .filterNot { line ->
                val trimmed = line.trim()
                ignoreRegexes.any { it.matches(trimmed) }
            }
            .joinToString("\n")
            .trim()
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

    private suspend fun runGemini(
        args: List<String>,
        workingDir: String?,
        ideBridge: GeminiBridge.Info?,
        timeoutSeconds: Long,
        onOutput: ((String) -> Unit)?,
    ): ShellUtils.Result {
        setupTerminalFiles()
        val command =
            buildList {
                    if (ideBridge != null) {
                        add("/usr/bin/env")
                        add("TMPDIR=${getTempDir().absolutePath}")
                        add("DEBUG=false")
                        add("DEBUG_MODE=false")
                        add("GEMINI_CLI_IDE_SERVER_PORT=${ideBridge.port}")
                        add("TERM_PROGRAM=vscode")
                        add("TERM_PROGRAM_VERSION=1.0.0")
                        add("VSCODE_PID=${android.os.Process.myPid()}")
                        add("EDITOR=vim")
                        add("VISUAL=vim")
                        add("GEMINI_CLI_IDE_AUTH_TOKEN=${ideBridge.token}")
                        add("GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}")
                        add("GEMINI_CLI_IDE_WORKSPACE_PATH=${ideBridge.workspacePath}")
                    }
                    add("/bin/bash")
                    add(localBinDir().child("gemini-cli-headless").absolutePath)
                    addAll(args)
                }
                .toTypedArray()
        return if (onOutput == null) {
            ShellUtils.runUbuntu(workingDir, *command, timeoutSeconds = timeoutSeconds)
        } else {
            ShellUtils.runUbuntuStreaming(
                workingDir,
                *command,
                timeoutSeconds = timeoutSeconds,
                onStdout = onOutput,
                onStderr = onOutput,
            )
        }
    }

    suspend fun workingDirFor(file: FileObject, projectRoot: FileObject?): String {
        return projectRoot?.getAbsolutePath()
            ?: (file.getParentFile() as? FileWrapper)?.getAbsolutePath()
            ?: file.getAbsolutePath()
    }

    fun stripCodeFences(text: String): String {
        val cleaned = cleanOutput(text).trim()
        val fenceStart = cleaned.indexOf("```")
        if (fenceStart == -1) return cleaned

        val firstLineEnd = cleaned.indexOf('\n', fenceStart)
        if (firstLineEnd == -1) return cleaned.substring(fenceStart + 3).trim().removeSuffix("```").trim()

        val contentStart = firstLineEnd + 1
        val lastFence = cleaned.lastIndexOf("```")
        if (lastFence <= contentStart) return cleaned.substring(contentStart).trim()

        return cleaned.substring(contentStart, lastFence).trim()
    }
}
