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
            "PRIVATE_DIR=${activity.filesDir.parentFile!!.absolutePath}",
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
            "IDE_SERVER_PORT=${bridge.port}",
            "IDE_AUTH_TOKEN=${bridge.token}",
            "IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        ).apply {
            val secureKey = com.rk.settings.SecureSettingsStore.get("ai_api_key")
            if (secureKey.isNotBlank()) {
                add("GEMINI_API_KEY=$secureKey")
                add("OPENCODE_API_KEY=$secureKey")
                add("AI_API_KEY=$secureKey")
            }
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
        val env = mutableMapOf(
            "DEBUG" to (System.getenv("XED_AGENT_DEBUG") ?: AiConfig.Debug.defaultDebugEnvValue),
            "DEBUG_MODE" to (System.getenv("XED_AGENT_DEBUG") ?: AiConfig.Debug.defaultDebugEnvValue),
            "AGENT_DEBUG_LOG_FILE" to (System.getenv("XED_AGENT_DEBUG_LOG_FILE") ?: AiConfig.Debug.defaultDebugLogFile),
            "AGENT_CONTEXT_TRACE_DIR" to (System.getenv("XED_AGENT_CONTEXT_TRACE_DIR") ?: AiConfig.Debug.defaultContextTraceDir),
        )
        env.putAll(extraVars)
        return env
    }

    fun bridgeEnvContent(bridge: IdeBridge.Info): String = buildString {
        appendLine("export IDE_SERVER_PORT=${bridge.port}")
        appendLine("export IDE_AUTH_TOKEN=${bridge.token}")
    }

    fun writeBridgeEnvFile(tmpDir: File, xedDir: File?, bridge: IdeBridge.Info) {
        val envContent = bridgeEnvContent(bridge)
        runCatching { File(tmpDir, AiConfig.Discovery.xedBridgeEnvFile).writeText(envContent) }
        runCatching { xedDir?.let { File(it, AiConfig.Discovery.ideEnvFile).writeText(envContent) } }
        runCatching { sandboxHomeDir().let { if (it.exists()) File(it, AiConfig.Discovery.xedBridgeEnvHomeFile).writeText(envContent) } }
    }

    fun buildMinimalBridgeEnv(bridge: IdeBridge.Info, workingDir: String): Array<String> = mutableListOf(
        "WKDIR=$workingDir",
        "IDE_SERVER_PORT=${bridge.port}",
        "IDE_AUTH_TOKEN=${bridge.token}",
        "IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        "TERM_PROGRAM=vscode",
        "TERM_PROGRAM_VERSION=1.0.0",
        "VSCODE_PID=${Process.myPid()}",
        "EDITOR=vim",
        "VISUAL=vim",
    ).apply {
        val secureKey = com.rk.settings.SecureSettingsStore.get("ai_api_key")
        if (secureKey.isNotBlank()) {
            add("GEMINI_API_KEY=$secureKey")
            add("OPENCODE_API_KEY=$secureKey")
            add("AI_API_KEY=$secureKey")
        }
    }.toTypedArray()
}
