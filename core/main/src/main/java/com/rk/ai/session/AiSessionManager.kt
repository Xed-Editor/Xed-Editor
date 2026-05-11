package com.rk.ai.session

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.activities.main.MainViewModel
import com.rk.ai.IdeBridge
import com.rk.ai.agents.AiAgent
import com.rk.ai.agents.GeminiAgent
import com.rk.ai.agents.OpenCodeAgent
import com.rk.ai.bridge.server.IdeBridgeServer
import com.rk.ai.service.IdeService
import com.rk.ai.service.IdeServiceImpl
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import com.rk.tabs.editor.createGeminiSheetSession
import com.rk.terminal.setupTerminalFiles
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiSessionManager {
    var session by mutableStateOf<TerminalSession?>(null)
    var cwd by mutableStateOf<String?>(null)
    var bridgeServer: IdeBridgeServer? = null
    var ideService: IdeService? = null
    var currentAgent by mutableStateOf<AiAgent>(GeminiAgent)

    private val agents = mapOf(
        "gemini" to GeminiAgent,
        "opencode" to OpenCodeAgent,
    )

    fun resolveAgent(type: String? = null): AiAgent =
        agents[type ?: Settings.ai_agent] ?: GeminiAgent

    fun availableAgents(): List<AiAgent> = agents.values.toList()

    fun switchAgent(type: String) {
        val newAgent = resolveAgent(type)
        if (newAgent != currentAgent) {
            stopSession()
            currentAgent = newAgent
            Settings.ai_agent = type
        }
    }

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("AiSessionManager", msg)
    }

    fun canReuseFor(requestedCwd: String): Boolean {
        if (session == null || !session!!.isRunning) return false
        val existingCwd = cwd ?: return false
        if (requestedCwd == existingCwd) return true
        if (existingCwd == "/" || existingCwd == "/storage/emulated/0" || existingCwd == "/home") return true
        return requestedCwd.startsWith("$existingCwd/")
    }

    suspend fun startSession(
        activity: Activity,
        viewModel: MainViewModel,
        workingDir: String,
        extraArgs: List<String> = emptyList(),
        agentType: String? = null,
    ): TerminalSession {
        currentAgent = resolveAgent(agentType)
        d("startSession agent=${currentAgent.name} workingDir=$workingDir")
        stopSession()

        return withContext(Dispatchers.IO) {
            IdeBridge.ensureStarted(viewModel)
            IdeBridge.setWorkspacePath(workingDir)
            val bridgeInfo = IdeBridge.getBridgeInfo()!!

            withContext(Dispatchers.Main) {
                ideService = IdeServiceImpl(viewModel)
                val newSession = createAgentSession(
                    activity = activity,
                    agent = currentAgent,
                    bridge = bridgeInfo,
                    workingDir = workingDir,
                    extraArgs = extraArgs,
                )
                session = newSession
                cwd = workingDir
                newSession
            }
        }
    }

    fun stopSession() {
        d("stopSession")
        session?.finishIfRunning()
        session = null
        cwd = null
        IdeBridge.stop()
        ideService = null
    }

    private fun createAgentSession(
        activity: Activity,
        agent: AiAgent,
        bridge: IdeBridge.Info,
        workingDir: String,
        extraArgs: List<String> = emptyList(),
    ): TerminalSession {
        setupTerminalFiles()
        val (shell, args) = agentSheetProcessArgs(agent, extraArgs, workingDir)
        return TerminalSession(
            shell,
            workingDir,
            args,
            buildAgentSheetEnv(activity, agent, workingDir, bridge),
            Settings.terminal_scrollback_buffer,
            com.rk.terminal.TerminalBackEnd(),
        ).also { it.mSessionName = "${agent.name}-sheet" }
    }

    private fun agentSheetProcessArgs(
        agent: AiAgent,
        extraArgs: List<String>,
        workingDir: String,
    ): Pair<String, Array<String>> {
        val sandbox = localBinDir().child("sandbox").absolutePath
        val launcher = localBinDir().child(agent.shellScriptName).absolutePath
        val command = buildList {
            add(sandbox)
            add("/bin/bash")
            add(launcher)
            addAll(agent.buildArgs(extraArgs, workingDir, Settings.ai_model.takeIf { it.isNotBlank() }))
        }
        return "/system/bin/sh" to arrayOf("sh", *command.toTypedArray())
    }

    private fun buildAgentSheetEnv(
        activity: Activity,
        agent: AiAgent,
        workingDir: String,
        bridge: IdeBridge.Info,
    ): Array<String> {
        val tmpDir = File(getTempDir(), "terminal/${agent.name}-sheet").apply { mkdirs() }
        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        val isFDroid = runCatching { com.rk.utils.isFDroid }.getOrDefault(false)

        val baseEnv = mutableListOf(
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "WKDIR=$workingDir",
            "PUBLIC_HOME=${activity.getExternalFilesDir(null)?.absolutePath}",
            "COLORTERM=truecolor",
            "TERM=xterm-256color",
            "TERM_PROGRAM=vscode",
            "TERM_PROGRAM_VERSION=1.0.0",
            "VSCODE_PID=${android.os.Process.myPid()}",
            "EDITOR=vim",
            "VISUAL=vim",
            "LANG=C.UTF-8",
            "LOCAL=${com.rk.file.localDir().absolutePath}",
            "PRIVATE_DIR=${activity.filesDir.parentFile!!.absolutePath}",
            "LD_LIBRARY_PATH=${com.rk.file.localLibDir().absolutePath}",
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
            "PATH=${System.getenv("PATH")}:${com.rk.file.localBinDir().absolutePath}",
            "ANDROID_ART_ROOT=${System.getenv("ANDROID_ART_ROOT").orEmpty()}",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA").orEmpty()}",
            "ANDROID_I18N_ROOT=${System.getenv("ANDROID_I18N_ROOT").orEmpty()}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT").orEmpty()}",
            "ANDROID_RUNTIME_ROOT=${System.getenv("ANDROID_RUNTIME_ROOT").orEmpty()}",
            "ANDROID_TZDATA_ROOT=${System.getenv("ANDROID_TZDATA_ROOT").orEmpty()}",
            "BOOTCLASSPATH=${System.getenv("BOOTCLASSPATH").orEmpty()}",
            "DEX2OATBOOTCLASSPATH=${System.getenv("DEX2OATBOOTCLASSPATH").orEmpty()}",
            "EXTERNAL_STORAGE=${System.getenv("EXTERNAL_STORAGE").orEmpty()}",
            "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
            "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
            "GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}",
            "GEMINI_CLI_IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
            "IDE_SERVER_PORT=${bridge.port}",
            "IDE_AUTH_TOKEN=${bridge.token}",
            "IDE_WORKSPACE_PATH=${com.rk.ai.ideWorkspacePath(workingDir)}",
        ).apply {
            addAll(agent.buildEnv(emptyMap()).map { "${it.key}=${it.value}" })
            if (!isFDroid && com.rk.file.localLibDir().child("libproot-loader.so").exists()) {
                add("PROOT_LOADER=${activity.applicationInfo.nativeLibraryDir}/libproot-loader.so")
                if (android.os.Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && File(activity.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()) {
                    add("PROOT_LOADER32=${activity.applicationInfo.nativeLibraryDir}/libproot-loader32.so")
                }
            }
            if (Settings.seccomp) add("SECCOMP=1")
        }
        return baseEnv.toTypedArray()
    }
}
