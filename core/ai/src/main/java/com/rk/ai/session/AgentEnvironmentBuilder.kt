package com.rk.ai.session

import android.app.Activity
import android.os.Build
import android.os.Process
import com.rk.ai.AiConfig
import com.rk.ai.IdeBridge
import com.rk.ai.agents.AiAgent
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import com.rk.utils.getTempDir
import java.io.File

data class AgentEnvironmentConfig(
    val activity: Activity,
    val workingDir: String,
    val bridge: IdeBridge.Info,
    val agent: AiAgent,
    val tmpSubdir: String,
    val extraEnv: Map<String, String> = emptyMap(),
)

object AgentEnvironmentBuilder {

    fun buildEnv(config: AgentEnvironmentConfig): Array<String> {
        val tmpDir = File(getTempDir(), "terminal/${config.tmpSubdir}").apply { mkdirs() }
        val linker = if (File(AiConfig.Paths.linker64Bit).exists()) AiConfig.Paths.linker64Bit else AiConfig.Paths.linker32Bit
        val isFDroid = runCatching { com.rk.utils.isFDroid }.getOrDefault(false)
        val activity = config.activity
        val workingDir = config.workingDir
        val bridge = config.bridge

        return mutableListOf(
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "WKDIR=$workingDir",
            "PUBLIC_HOME=${activity.getExternalFilesDir(null)?.absolutePath}",
            "COLORTERM=truecolor",
            "TERM=xterm-256color",
            "TERM_PROGRAM=vscode",
            "TERM_PROGRAM_VERSION=1.0.0",
            "VSCODE_PID=${Process.myPid()}",
            "EDITOR=vim",
            "VISUAL=vim",
            "LANG=C.UTF-8",
            "LOCAL=${localDir().absolutePath}",
            "PRIVATE_DIR=${activity.filesDir.parentFile?.absolutePath ?: activity.filesDir.absolutePath}",
            "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
            "EXT_HOME=${sandboxHomeDir()}",
            "HOME=${if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath}",
            "PROMPT_DIRTRIM=2",
            "LINKER=$linker",
            "NATIVE_LIB_DIR=${activity.applicationInfo.nativeLibraryDir}",
            "FDROID=$isFDroid",
            "SANDBOX=${Settings.sandbox}",
            "TMP_DIR=${tmpDir.absolutePath}",
            "TMPDIR=${tmpDir.absolutePath}",
            "TZ=UTC",
            "DOTNET_GCHeapHardLimit=1C0000000",
            "SOURCE_DIR=${activity.applicationInfo.sourceDir}",
            "TERMUX_X11_SOURCE_DIR=${runCatching { com.rk.utils.getSourceDirOfPackage(activity, "com.termux.x11") }.getOrDefault("")}",
            "DISPLAY=:0",
            "PATH=${System.getenv("PATH")}:${localBinDir().absolutePath}",
            "ANDROID_ART_ROOT=${System.getenv("ANDROID_ART_ROOT").orEmpty()}",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA").orEmpty()}",
            "ANDROID_I18N_ROOT=${System.getenv("ANDROID_I18N_ROOT").orEmpty()}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT").orEmpty()}",
            "ANDROID_RUNTIME_ROOT=${System.getenv("ANDROID_RUNTIME_ROOT").orEmpty()}",
            "ANDROID_TZDATA_ROOT=${System.getenv("ANDROID_TZDATA_ROOT").orEmpty()}",
            "BOOTCLASSPATH=${System.getenv("BOOTCLASSPATH").orEmpty()}",
            "DEX2OATBOOTCLASSPATH=${System.getenv("DEX2OATBOOTCLASSPATH").orEmpty()}",
            "EXTERNAL_STORAGE=${System.getenv("EXTERNAL_STORAGE").orEmpty()}",
            "XED_IDE_URL=http://${bridge.host}:${bridge.port}",
            "XED_IDE_HOST=${bridge.host}",
            "XED_IDE_PORT=${bridge.port}",
            "XED_IDE_AUTH_TOKEN=${bridge.token}",
            "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
            "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
            "GEMINI_CLI_IDE_PID=${Process.myPid()}",
            "GEMINI_CLI_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "CODEX_IDE_SERVER_PORT=${bridge.port}",
            "CODEX_IDE_AUTH_TOKEN=${bridge.token}",
            "CODEX_IDE_PID=${Process.myPid()}",
            "CODEX_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "ANTIGRAVITY_IDE_SERVER_PORT=${bridge.port}",
            "ANTIGRAVITY_IDE_AUTH_TOKEN=${bridge.token}",
            "ANTIGRAVITY_IDE_PID=${Process.myPid()}",
            "ANTIGRAVITY_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "CLAUDE_IDE_SERVER_PORT=${bridge.port}",
            "CLAUDE_IDE_AUTH_TOKEN=${bridge.token}",
            "CLAUDE_IDE_PID=${Process.myPid()}",
            "CLAUDE_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "CLAUDE_CODE_IDE_SERVER_PORT=${bridge.port}",
            "CLAUDE_CODE_IDE_AUTH_TOKEN=${bridge.token}",
            "CLAUDE_CODE_IDE_PID=${Process.myPid()}",
            "CLAUDE_CODE_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "IDE_SERVER_PORT=${bridge.port}",
            "IDE_AUTH_TOKEN=${bridge.token}",
            "IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "MCP_HOST=${bridge.host}",
            "MCP_PORT=${bridge.port}",
            "MCP_AUTH_TOKEN=${bridge.token}",
        ).apply {
            addAll(config.agent.buildEnv(emptyMap()).map { "${it.key}=${it.value}" })
            addAll(config.extraEnv.map { "${it.key}=${it.value}" })
            if (!isFDroid && localLibDir().child("libproot-loader.so").exists()) {
                add("PROOT_LOADER=${activity.applicationInfo.nativeLibraryDir}/libproot-loader.so")
                if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && File(activity.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()) {
                    add("PROOT_LOADER32=${activity.applicationInfo.nativeLibraryDir}/libproot-loader32.so")
                }
            }
            if (Settings.seccomp) add("SECCOMP=1")
        }.toTypedArray()
    }

    fun buildDebugEnv(extraVars: Map<String, String> = emptyMap()): Map<String, String> {
        val debugValue = System.getenv("XED_GEMINI_DEBUG") ?: AiConfig.Debug.defaultDebugEnvValue
        val env = mutableMapOf(
            "DEBUG" to debugValue,
            "DEBUG_MODE" to debugValue,
            "GEMINI_DEBUG_LOG_FILE" to (System.getenv("XED_GEMINI_DEBUG_LOG_FILE") ?: AiConfig.Debug.defaultDebugLogFile),
            "GEMINI_CONTEXT_TRACE_DIR" to (System.getenv("XED_GEMINI_CONTEXT_TRACE_DIR") ?: AiConfig.Debug.defaultContextTraceDir),
        )
        env.putAll(extraVars)
        return env
    }

    fun bridgeEnvContent(bridge: IdeBridge.Info): String = buildString {
        appendLine("export XED_IDE_URL=http://${bridge.host}:${bridge.port}")
        appendLine("export XED_IDE_HOST=${bridge.host}")
        appendLine("export XED_IDE_PORT=${bridge.port}")
        appendLine("export XED_IDE_AUTH_TOKEN=${bridge.token}")
        appendLine("export IDE_SERVER_PORT=${bridge.port}")
        appendLine("export IDE_AUTH_TOKEN=${bridge.token}")
        appendLine("export CODEX_IDE_SERVER_PORT=${bridge.port}")
        appendLine("export CODEX_IDE_AUTH_TOKEN=${bridge.token}")
        appendLine("export CODEX_IDE_PID=${Process.myPid()}")
        appendLine("export ANTIGRAVITY_IDE_SERVER_PORT=${bridge.port}")
        appendLine("export ANTIGRAVITY_IDE_AUTH_TOKEN=${bridge.token}")
        appendLine("export ANTIGRAVITY_IDE_PID=${Process.myPid()}")
        appendLine("export CLAUDE_IDE_SERVER_PORT=${bridge.port}")
        appendLine("export CLAUDE_IDE_AUTH_TOKEN=${bridge.token}")
        appendLine("export CLAUDE_IDE_PID=${Process.myPid()}")
        appendLine("export CLAUDE_CODE_IDE_SERVER_PORT=${bridge.port}")
        appendLine("export CLAUDE_CODE_IDE_AUTH_TOKEN=${bridge.token}")
        appendLine("export CLAUDE_CODE_IDE_PID=${Process.myPid()}")
        appendLine("export MCP_PORT=${bridge.port}")
        appendLine("export MCP_AUTH_TOKEN=${bridge.token}")
    }

    fun writeBridgeEnvFile(tmpDir: File, xedDir: File?, bridge: IdeBridge.Info) {
        val envContent = bridgeEnvContent(bridge)
        runCatching { File(tmpDir, AiConfig.Discovery.xedBridgeEnvFile).writeText(envContent) }
        runCatching { xedDir?.let { File(it, AiConfig.Discovery.ideEnvFile).writeText(envContent) } }
        runCatching { sandboxHomeDir().let { if (it.exists()) File(it, AiConfig.Discovery.xedBridgeEnvHomeFile).writeText(envContent) } }
    }

    fun buildMinimalBridgeEnv(bridge: IdeBridge.Info, workingDir: String): List<String> = listOf(
        "WKDIR=$workingDir",
        "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
        "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
        "GEMINI_CLI_IDE_PID=${Process.myPid()}",
        "GEMINI_CLI_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "CODEX_IDE_SERVER_PORT=${bridge.port}",
        "CODEX_IDE_AUTH_TOKEN=${bridge.token}",
        "CODEX_IDE_PID=${Process.myPid()}",
        "CODEX_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "ANTIGRAVITY_IDE_SERVER_PORT=${bridge.port}",
        "ANTIGRAVITY_IDE_AUTH_TOKEN=${bridge.token}",
        "ANTIGRAVITY_IDE_PID=${Process.myPid()}",
        "ANTIGRAVITY_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "CLAUDE_IDE_SERVER_PORT=${bridge.port}",
        "CLAUDE_IDE_AUTH_TOKEN=${bridge.token}",
        "CLAUDE_IDE_PID=${Process.myPid()}",
        "CLAUDE_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "CLAUDE_CODE_IDE_SERVER_PORT=${bridge.port}",
        "CLAUDE_CODE_IDE_AUTH_TOKEN=${bridge.token}",
        "CLAUDE_CODE_IDE_PID=${Process.myPid()}",
        "CLAUDE_CODE_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "IDE_SERVER_PORT=${bridge.port}",
        "IDE_AUTH_TOKEN=${bridge.token}",
        "IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "TERM_PROGRAM=vscode",
        "TERM_PROGRAM_VERSION=1.0.0",
        "VSCODE_PID=${Process.myPid()}",
        "EDITOR=vim",
        "VISUAL=vim",
    )
}
