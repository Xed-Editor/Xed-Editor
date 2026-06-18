package com.rk.terminal

import android.app.Activity
import android.content.Context
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
import com.rk.utils.application
import com.rk.utils.getSourceDirOfPackage
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import kotlinx.coroutines.runBlocking

object MkSession {

    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        sessionId: String,
        isExtraction: Boolean = false,
    ): Pair<TerminalSession, SessionPwd> {
        val prefixPath = File(context.filesDir, "usr").absolutePath

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
                "PATH" to "$prefixPath/bin:$prefixPath/bin/applets:${System.getenv("PATH")}:${localBinDir(context).absolutePath}",
            )

        val workingDir = runBlocking { getPwd(context) }

        val tmpDir = File(getTempDir(), "terminal/$sessionId")
        if (tmpDir.exists()) {
            tmpDir.deleteRecursively()
        }
        tmpDir.mkdirs()

        val env =
            mutableListOf(
                "PREFIX=$prefixPath",
                "LD_PRELOAD=$prefixPath/lib/libtermux-exec.so",
                "PROOT=${application!!.applicationInfo.nativeLibraryDir}/libproot.so",
                "PROOT_LOADER=${application!!.applicationInfo.nativeLibraryDir}/libloader.so",
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "WKDIR=${workingDir}",
                "PUBLIC_HOME=${context.getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "DEBUG=${BuildConfig.DEBUG}",
                "LOCAL=${localDir(context).absolutePath}",
                "PRIVATE_DIR=${context.filesDir.parentFile!!.absolutePath}",
                "LD_LIBRARY_PATH=$prefixPath/lib:${localLibDir(context).absolutePath}",
                "EXT_HOME=${sandboxHomeDir(context).absolutePath}",
                "HOME=${sandboxHomeDir(context).absolutePath}",
                "PROMPT_DIRTRIM=2",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                "NATIVE_LIB_DIR=${context.applicationInfo.nativeLibraryDir}",
                "TMP_DIR=${getTempDir()}",
                "TMPDIR=${getTempDir()}",
                "TZ=UTC",
                "DOTNET_GCHeapHardLimit=1C0000000",
                "SOURCE_DIR=${context.applicationInfo.sourceDir}",
                "TERMUX_X11_SOURCE_DIR=${getSourceDirOfPackage(application!!,"com.termux.x11")}",
                "DISPLAY=:0",
            )

        val loader32 = "${context.applicationInfo.nativeLibraryDir}/libloader32.so"
        if (File(loader32).exists()) {
            env.add("PROOT_LOADER_32=$loader32")
        }

        env.addAll(envVariables.map { "${it.key}=${it.value}" })

        pendingCommand?.env?.let { env.addAll(it) }

        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        val bashPath = "$prefixPath/bin/bash"

        val actualShell: String = linker
        val actualArgs: Array<String>

        if (pendingCommand == null) {
            actualArgs = arrayOf(linker, bashPath, "--login")
        } else {
            var exe = pendingCommand!!.exe
            if (exe.startsWith("/usr/bin/")) {
                exe = prefixPath + "/bin/" + exe.substring(9)
            } else if (exe.startsWith("/usr/")) {
                exe = prefixPath + "/" + exe.substring(5)
            } else if (exe.startsWith("/bin/")) {
                exe = prefixPath + "/bin/" + exe.substring(5)
            } else if (!exe.startsWith("/") && !exe.startsWith(".")) {
                val fileInPrefix = File("$prefixPath/bin/$exe")
                if (fileInPrefix.exists()) {
                    exe = fileInPrefix.absolutePath
                }
            }

            val cmdArgs = mutableListOf(linker, bashPath, "-c", """"$1" "$@"""", "--", exe)
            cmdArgs.addAll(pendingCommand!!.args)
            actualArgs = cmdArgs.toTypedArray()
        }

        pendingCommand = null

        return TerminalSession(
            actualShell,
            localDir(context).absolutePath,
            actualArgs,
            env.toTypedArray(),
            Settings.terminal_scrollback_buffer,
            sessionClient,
        ) to workingDir
    }
}

suspend fun getPwd(context: Context): String {
    val pendingWorkingDir = pendingCommand?.workingDir
    if (pendingWorkingDir != null) {
        return pendingWorkingDir
    }

    if (context is Activity && context.intent.hasExtra("cwd")) {
        return context.intent.getStringExtra("cwd").toString()
    }

    val currentTab = MainActivity.instance?.viewModel?.tabManager?.currentTab
    if (Settings.project_as_pwd) {
        currentTab?.let {
            if (it is EditorTab && it.file is FileWrapper) {
                val parent = it.file.getParentFile()
                if (parent != null && parent is FileWrapper) {
                    return if (Settings.sandbox) {
                        parent.getAbsolutePath().removePrefix(localDir(context).absolutePath)
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
                        parent.getAbsolutePath().removePrefix(localDir(context).absolutePath)
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
        sandboxHomeDir(context).absolutePath
    }
}
