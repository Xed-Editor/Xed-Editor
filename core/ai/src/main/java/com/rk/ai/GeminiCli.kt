package com.rk.ai

import com.rk.ai.AiConfig
import com.rk.ai.session.AgentEnvironmentBuilder
import com.rk.exec.ShellUtils
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.terminal.setupTerminalFiles
import com.rk.utils.getTempDir
import java.io.File

object GeminiCli {
    suspend fun prompt(
        prompt: String,
        workingDir: String? = null,
        projectDir: String? = workingDir,
        ideBridge: IdeBridge.Info? = null,
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
        ideBridge: IdeBridge.Info? = null,
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
        // Remove all ANSI escape sequences, not just colors
        val ansiRegex = Regex("\\u001B\\[[;\\d]*[A-Za-z]")
        val ignoreRegexes = listOf(
            Regex("^(INFO|WARN|ERROR|DEBUG|TRACE)\\s+(ClearcutLogger|Flush|Starting Gemini CLI).*"),
            Regex(".*No GEMINI_API_KEY/GOOGLE_API_KEY.*"),
            Regex(".*No API key found\\. Headless mode.*"),
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
        ideBridge: IdeBridge.Info?,
        timeoutSeconds: Long,
        onOutput: ((String) -> Unit)?,
    ): ShellUtils.Result {
        setupTerminalFiles()
        val extraEnv = mutableMapOf<String, String>()
        if (ideBridge != null) {
            val tmpDir = File(getTempDir(), "terminal/gemini-sheet")
            extraEnv.putAll(AgentEnvironmentBuilder.buildMinimalBridgeEnv(ideBridge, workingDir ?: "").map { 
                val split = it.split("=", limit = 2)
                split[0] to split[1]
            }.toMap())
            extraEnv.putAll(AgentEnvironmentBuilder.buildDebugEnv())
            extraEnv["TMPDIR"] = tmpDir.absolutePath
            extraEnv["TMP_DIR"] = tmpDir.absolutePath
        }

        val command = listOf("/bin/bash", localBinDir().child("gemini-cli-headless").absolutePath) + args
        
        return if (onOutput == null) {
            ShellUtils.runUbuntu(workingDir, *command.toTypedArray(), extraEnv = extraEnv, timeoutSeconds = timeoutSeconds)
        } else {
            ShellUtils.runUbuntuStreaming(
                workingDir,
                *command.toTypedArray(),
                extraEnv = extraEnv,
                timeoutSeconds = timeoutSeconds,
                onStdout = onOutput,
                onStderr = onOutput,
            )
        }
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
