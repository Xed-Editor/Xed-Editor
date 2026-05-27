package com.rk.terminal

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.rk.activities.main.MainActivity
import com.rk.exec.pendingCommand
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.getSourceDirOfPackage
import com.rk.utils.getTempDir
import com.rk.utils.isFDroid
import com.rk.terminal.NEXT_STAGE
import com.rk.terminal.getNextStage
import com.rk.terminal.setupTerminalFiles
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object MkSession {

    fun createSession(
        activity: Activity,
        sessionClient: TerminalSessionClient,
        sessionId: String,
    ): Pair<TerminalSession, SessionPwd> {
        val envVariables =
            mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE"),
                "PATH" to "${System.getenv("PATH")}:${localBinDir().absolutePath}",
            )

        val workingDir = runBlocking { getPwd(activity, activity.intent) }

        val tmpDir = File(getTempDir(), "terminal/$sessionId")

        if (tmpDir.exists()) {
            tmpDir.deleteRecursively()
        }

        tmpDir.mkdirs()

        val currentTab = MainActivity.instance?.viewModel?.tabManager?.currentTab
        val activeFile = if (currentTab is EditorTab) currentTab.file.getAbsolutePath() else ""
        val activeProject = MainActivity.instance?.viewModel?.tabManager?.currentTab?.let { 
            if (it is EditorTab) it.projectRoot?.getAbsolutePath() else null 
        } ?: ""

        val env =
            mutableListOf(
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "WKDIR=${workingDir}",
                "PUBLIC_HOME=${activity.getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "DEBUG=${BuildConfig.DEBUG}",
                "LOCAL=${localDir().absolutePath}",
                "PRIVATE_DIR=${activity.filesDir.parentFile!!.absolutePath}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "EXT_HOME=${sandboxHomeDir()}",
                "HOME=${if (Settings.sandbox){ "/home"} else{ sandboxHomeDir()}}",
                "PROMPT_DIRTRIM=2",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                "NATIVE_LIB_DIR=${activity.applicationInfo.nativeLibraryDir}",
                "FDROID=${isFDroid}",
                "SANDBOX=${Settings.sandbox}",
                "TMP_DIR=${getTempDir()}",
                "TMPDIR=${getTempDir()}",
                "TZ=UTC",
                "DOTNET_GCHeapHardLimit=1C0000000",
                "SOURCE_DIR=${activity.applicationInfo.sourceDir}",
                "TERMUX_X11_SOURCE_DIR=${getSourceDirOfPackage(activity.application!!,"com.termux.x11")}",
                "DISPLAY=:0",
                "XED_ACTIVE_FILE=$activeFile",
                "XED_ACTIVE_PROJECT=$activeProject",
                "IDE_ACTIVE_FILE=$activeFile",
                "IDE_ACTIVE_PROJECT=$activeProject",
            )

        if (!isFDroid) {
            env.add("PROOT_LOADER=${activity.applicationInfo.nativeLibraryDir}/libproot-loader.so")
            if (
                Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() &&
                    File(activity.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()
            ) {
                env.add("PROOT_LOADER32=${activity.applicationInfo.nativeLibraryDir}/libproot-loader32.so")
            }
        }

        if (Settings.seccomp) {
            env.add("SECCOMP=1")
        }

        env.addAll(envVariables.map { "${it.key}=${it.value}" })

        pendingCommand?.env?.let { env.addAll(it) }

        setupTerminalFiles()

        val sandboxSH = localBinDir().child("sandbox")
        val setupSH = localBinDir().child("setup")

        val args: Array<String>

        val shell =
            if (pendingCommand == null) {
                args =
                    if (Settings.sandbox) {
                        arrayOf(sandboxSH.absolutePath)
                    } else {
                        arrayOf()
                    }
                "/system/bin/sh"
            } else if (pendingCommand!!.sandbox.not()) {
                args = pendingCommand!!.args
                pendingCommand!!.exe
            } else {
                args =
                    mutableListOf(sandboxSH.absolutePath, pendingCommand!!.exe, *pendingCommand!!.args)
                        .toTypedArray<String>()

                "/system/bin/sh"
            }

        val actualShell: String
        val actualArgs: Array<String> =
            if (runBlocking(Dispatchers.IO) { getNextStage(activity) } == NEXT_STAGE.EXTRACTION) {
                actualShell = "/system/bin/sh"
                mutableListOf("-c", setupSH.absolutePath, *args).toTypedArray()
            } else {
                actualShell = shell
                arrayOf("-c", *args)
            }

        pendingCommand = null

        return TerminalSession(
            actualShell,
            localDir().absolutePath,
            actualArgs,
            env.toTypedArray(),
            Settings.terminal_scrollback_buffer,
            sessionClient,
        ) to workingDir
    }

    suspend fun getPwd(activity: Activity, intent: Intent): String {
        val pendingWorkingDir = pendingCommand?.workingDir
        if (pendingWorkingDir != null) {
            return pendingWorkingDir
        }

        if (intent.hasExtra("cwd")) {
            return intent.getStringExtra("cwd").toString()
        }

        val currentTab = MainActivity.instance?.viewModel?.tabManager?.currentTab
        if (Settings.project_as_pwd) {
            currentTab?.let {
                if (it is EditorTab && it.file is FileWrapper) {
                    val parent = it.file.getParentFile()
                    if (parent != null && parent is FileWrapper) {
                        return if (Settings.sandbox) {
                            parent.getAbsolutePath().removePrefix(localDir().absolutePath)
                        } else {
                            parent.getAbsolutePath()
                        }
                    }
                }
            }
        } else {
            currentTab?.let {
                if (it is EditorTab && it.file is FileWrapper) {
                    val parent = it.file.getParentFile()
                    if (parent != null && parent is FileWrapper) {
                        return if (Settings.sandbox) {
                            parent.getAbsolutePath().removePrefix(localDir().absolutePath)
                        } else {
                            parent.getAbsolutePath()
                        }
                    }
                }
            }
        }
        return if (Settings.sandbox) {
            "/home"
        } else {
            sandboxHomeDir().absolutePath
        }
    }
}
