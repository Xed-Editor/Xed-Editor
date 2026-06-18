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
    ): Pair<TerminalSession, SessionPwd> {
        val prefixPath = File(context.filesDir, "usr").absolutePath
        val homePath = sandboxHomeDir(context).absolutePath
        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        val workingDir = runBlocking { getPwd(context) }

        val tmpDir = File(getTempDir(), "terminal/$sessionId")
        if (tmpDir.exists()) {
            tmpDir.deleteRecursively()
        }
        tmpDir.mkdirs()

        val env = com.rk.exec.ProcessEnv.getEnvironment(context, tmpDir, workingDir, linker)
            .map { "${it.key}=${it.value}" }
            .toMutableList()

        pendingCommand?.env?.let { env.addAll(it) }

        val bashPath = "$prefixPath/bin/bash"

        val actualShell: String = linker
        val actualArgs: Array<String>

        if (pendingCommand == null) {
            actualArgs = arrayOf(linker, bashPath, "--login")
        } else {
            var exe = pendingCommand!!.exe
            exe = com.rk.exec.ProcessEnv.resolvePath(exe, prefixPath, homePath)
            if (!exe.startsWith("/") && !exe.startsWith(".")) {
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
