package com.rk.ai

import com.rk.ai.agents.AiAgent
import com.rk.ai.session.AgentEnvironmentBuilder
import com.rk.exec.ShellUtils
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.terminal.setupTerminalFiles
import com.rk.utils.getTempDir
import java.io.File

object AgentCli {

    suspend fun runInteractive(
        prompt: String,
        agent: AiAgent = com.rk.ai.session.AiSessionManager.currentAgent,
        workingDir: String? = null,
        ideBridge: IdeBridge.Info? = null,
        model: String? = null,
        timeoutSeconds: Long = 180,
        onOutput: ((String) -> Unit)? = null,
    ): ShellUtils.Result {
        return runCli(
            args = agent.buildArgs(
                listOf("--approval-mode=default", "-p", prompt),
                workingDir ?: "",
                model ?: resolvedConfiguredModelForAgent(agent)
            ),
            binaryName = agent.cliBinaryName,
            workingDir = workingDir,
            ideBridge = ideBridge,
            timeoutSeconds = timeoutSeconds,
            onOutput = onOutput,
        )
    }

    suspend fun runAgent(
        prompt: String,
        agent: AiAgent = com.rk.ai.session.AiSessionManager.currentAgent,
        workingDir: String? = null,
        ideBridge: IdeBridge.Info? = null,
        model: String? = null,
        timeoutSeconds: Long = 600,
        onOutput: ((String) -> Unit)? = null,
    ): ShellUtils.Result {
        return runCli(
            args = agent.buildArgs(
                listOf("--approval-mode=auto_edit", "-p", prompt),
                workingDir ?: "",
                model ?: resolvedConfiguredModelForAgent(agent)
            ),
            binaryName = agent.cliBinaryName,
            workingDir = workingDir,
            ideBridge = ideBridge,
            timeoutSeconds = timeoutSeconds,
            onOutput = onOutput,
        )
    }

    suspend fun runInteractive(
        prompt: String,
        agentName: String,
        workingDir: String? = null,
        ideBridge: IdeBridge.Info? = null,
        model: String? = null,
        timeoutSeconds: Long = 180,
        onOutput: ((String) -> Unit)? = null,
    ): ShellUtils.Result =
        runInteractive(
            prompt,
            com.rk.ai.session.AiSessionManager.resolveAgent(agentName),
            workingDir,
            ideBridge,
            model,
            timeoutSeconds,
            onOutput
        )

    suspend fun runAgent(
        prompt: String,
        agentName: String,
        workingDir: String? = null,
        ideBridge: IdeBridge.Info? = null,
        model: String? = null,
        timeoutSeconds: Long = 600,
        onOutput: ((String) -> Unit)? = null,
    ): ShellUtils.Result =
        runAgent(
            prompt,
            com.rk.ai.session.AiSessionManager.resolveAgent(agentName),
            workingDir,
            ideBridge,
            model,
            timeoutSeconds,
            onOutput
        )

    fun cleanOutput(text: String): String {
        val ansiRegex = Regex("\\u001B\\[[;\\d]*[A-Za-z]")
        val ignoreRegexes = listOf(
            Regex("^(INFO|WARN|ERROR|DEBUG|TRACE)\\s+(ClearcutLogger|Flush|Starting).*"),
            Regex(".*No GEMINI_API_KEY/GOOGLE_API_KEY.*"),
            Regex(".*No API key found\\. Headless mode.*"),
            Regex(".*No OPENCODE_API_KEY.*"),
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

    fun stripCodeFences(text: String): String {
        val cleaned = cleanOutput(text).trim()
        val fenceStart = cleaned.indexOf("```")
        if (fenceStart == -1) return cleaned
        val contentAfterStart = cleaned.substring(fenceStart + 3).trimStart()
        val firstLineEnd = contentAfterStart.indexOf('\n')
        if (firstLineEnd == -1) return contentAfterStart.removeSuffix("```").trim()
        val afterLang = contentAfterStart.substring(firstLineEnd + 1)
        val lastFence = afterLang.lastIndexOf("```")
        return if (lastFence < 0) afterLang.trim() else afterLang.substring(0, lastFence).trim()
    }

    private suspend fun runCli(
        args: List<String>,
        binaryName: String,
        workingDir: String?,
        ideBridge: IdeBridge.Info?,
        timeoutSeconds: Long,
        onOutput: ((String) -> Unit)?,
    ): ShellUtils.Result {
        setupTerminalFiles()
        val extraEnv = mutableMapOf<String, String>()
        if (ideBridge != null) {
            val tmpDir = File(getTempDir(), "terminal/${binaryName.replace("-headless", "")}-sheet")
            extraEnv.putAll(AgentEnvironmentBuilder.buildMinimalBridgeEnv(ideBridge, workingDir ?: "").map { 
                val parts = it.split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }.toMap())
            extraEnv.putAll(AgentEnvironmentBuilder.buildDebugEnv())
            extraEnv["TMPDIR"] = tmpDir.absolutePath
            extraEnv["TMP_DIR"] = tmpDir.absolutePath
        }
        
        val command = arrayOf("/bin/bash", localBinDir().child(binaryName).absolutePath, *args.toTypedArray())
        
        return if (onOutput == null) {
            ShellUtils.runUbuntu(
                workingDir = workingDir,
                extraEnv = extraEnv,
                command = *command,
                timeoutSeconds = timeoutSeconds
            )
        } else {
            ShellUtils.runUbuntuStreaming(
                workingDir = workingDir,
                extraEnv = extraEnv,
                command = *command,
                timeoutSeconds = timeoutSeconds,
                onStdout = onOutput,
                onStderr = onOutput,
            )
        }
    }
}
