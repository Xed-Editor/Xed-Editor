package com.rk.terminal

import android.os.Build
import com.rk.activities.main.MainActivity
import com.rk.activities.terminal.Terminal
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
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import kotlinx.coroutines.runBlocking

object MkSession {

    fun createSession(
        activity: Terminal,
        sessionClient: TerminalSessionClient,
        sessionId: String,
    ): Pair<TerminalSession, SessionPwd> {
        with(activity) {
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

            val workingDir = runBlocking { getPwd() }

            val tmpDir = File(getTempDir(), "terminal/$sessionId")

            if (tmpDir.exists()) {
                tmpDir.deleteRecursively()
            }

            tmpDir.mkdirs()

            val env =
                mutableListOf(
                    "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                    "WKDIR=${workingDir}",
                    "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                    "COLORTERM=truecolor",
                    "TERM=xterm-256color",
                    "LANG=C.UTF-8",
                    "DEBUG=${BuildConfig.DEBUG}",
                    "LOCAL=${localDir().absolutePath}",
                    "PRIVATE_DIR=${filesDir.parentFile!!.absolutePath}",
                    "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                    "EXT_HOME=${sandboxHomeDir()}",
                    "HOME=${if (Settings.sandbox){ "/home"} else{ sandboxHomeDir()}}",
                    "PROMPT_DIRTRIM=2",
                    "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                    "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                    "FDROID=${isFDroid}",
                    "SANDBOX=${Settings.sandbox}",
                    "TMP_DIR=${getTempDir()}",
                    "TMPDIR=${getTempDir()}",
                    "TZ=UTC",
                    "DOTNET_GCHeapHardLimit=1C0000000",
                    "SOURCE_DIR=${applicationInfo.sourceDir}",
                    "TERMUX_X11_SOURCE_DIR=${getSourceDirOfPackage(application!!,"com.termux.x11")}",
                    "DISPLAY=:0",
                )

            if (!isFDroid) {
                env.add("PROOT_LOADER=${applicationInfo.nativeLibraryDir}/libproot-loader.so")
                if (
                    Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() &&
                        File(applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()
                ) {
                    env.add("PROOT_LOADER32=${applicationInfo.nativeLibraryDir}/libproot-loader32.so")
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
                if (installNextStage != null && installNextStage == NEXT_STAGE.EXTRACTION) {
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
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            ) to workingDir
        }
    }
}

suspend fun Terminal.getPwd(): String {
    val pendingWorkingDir = pendingCommand?.workingDir
    if (pendingWorkingDir != null) {
        return pendingWorkingDir
    }

    if (intent.hasExtra("cwd")) {
        return intent.getStringExtra("cwd").toString()
    }

    if (Settings.project_as_pwd) {
        //        if (currentProject != null && currentProject is FileWrapper) {
        //            val absolutePath = currentProject!!.getAbsolutePath()
        //            return if (Settings.sandbox) {
        //                absolutePath.removePrefix(localDir().absolutePath)
        //            } else {
        //                absolutePath
        //            }
        //        }

        MainActivity.instance?.viewModel?.currentTab?.let {
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
        MainActivity.instance?.viewModel?.currentTab?.let {
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
