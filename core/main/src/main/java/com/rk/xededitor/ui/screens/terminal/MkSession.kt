package com.rk.xededitor.ui.screens.terminal

import android.os.Environment
import com.rk.file.FileWrapper
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localLibDir
import com.rk.libcommons.pendingCommand
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.ui.activities.terminal.Terminal
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    fun createSession(
        activity: Terminal, sessionClient: TerminalSessionClient, session_id: String
    ): TerminalSession {
        with(activity) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            fun getPwd(): String {
                return if (intent.hasExtra("cwd")) {
                    intent.getStringExtra("cwd").toString()
                } else if (MainActivity.activityRef.get() != null && ProjectManager.projects.isNotEmpty()) {
                    val fileObject =
                        ProjectManager.CurrentProject.get(MainActivity.activityRef.get()!!)
                    var path = Environment.getExternalStorageDirectory().path
                    if (fileObject is FileWrapper) {
                        path = fileObject.getAbsolutePath()
                    }
                    path
                } else {
                    Environment.getExternalStorageDirectory().path
                }
            }

            val workingDir = pendingCommand?.workingDir ?: getPwd()

            val tmpDir = File(getTempDir(), "terminal/$session_id")

            if (tmpDir.exists()) {
                tmpDir.deleteRecursively()
            }

            tmpDir.mkdirs()

            val env = mutableListOf(
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "HOME=${application!!.filesDir.path}",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}"
            )


            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            val initHost = localBinDir().child("init-host")
            if (initHost.exists().not()) {
                initHost.createFileIfNot()
                initHost.writeText(assets.open("terminal/init-host.sh").bufferedReader()
                    .use { it.readText() })
            }
            val init = localBinDir().child("init")
            if (init.exists().not()) {
                init.createFileIfNot()
                init.writeText(assets.open("terminal/init.sh").bufferedReader()
                    .use { it.readText() })
            }

            val args: Array<String>

            val shell = if (pendingCommand == null) {
                args = if (PreferencesData.getString(
                        PreferencesKeys.TERMINAL_RUNTIME, "Alpine"
                    ) == "Android"
                ) {
                    arrayOf()
                } else {
                    arrayOf("-c", initHost.absolutePath)
                }
                "/system/bin/sh"
            } else if (pendingCommand!!.alpine.not()) {
                args = pendingCommand!!.args
                pendingCommand!!.shell
            } else {
                args = mutableListOf(
                    "-c", initHost.absolutePath, pendingCommand!!.shell
                ).also<MutableList<String>> {
                    it.addAll(pendingCommand!!.args)
                }.toTypedArray<String>()

                "/system/bin/sh"
            }

            pendingCommand = null
            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }

    }
}