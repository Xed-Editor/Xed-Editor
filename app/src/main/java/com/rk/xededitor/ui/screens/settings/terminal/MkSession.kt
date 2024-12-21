package com.rk.xededitor.ui.screens.settings.terminal

import android.app.Activity
import android.os.Environment
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localLibDir
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.ui.activities.settings.Terminal
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    fun createSession(activity: Terminal, sessionClient: TerminalSessionClient,session_id:String): TerminalSession {
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
                    ProjectManager.currentProject.get(MainActivity.activityRef.get()!!).absolutePath
                } else {
                    Environment.getExternalStorageDirectory().path
                }
            }

            val workingDir = getPwd()

            val tmpDir = File(getTempDir(), "terminal/$session_id")

            if (tmpDir.exists()) {
                tmpDir.deleteRecursively()
                tmpDir.mkdirs()
            } else {
                tmpDir.mkdirs()
            }

            val env = mutableListOf(
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "HOME=" + filesDir.absolutePath,
                "PUBLIC_HOME=" + getExternalFilesDir(null)?.absolutePath,
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PREFIX=${filesDir.parentFile.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
            )



            env.addAll(envVariables.map { "${it.key}=${it.value}" })


            val shell = "/system/bin/sh"
            val args = if (PreferencesData.getString(PreferencesKeys.TERMINAL_RUNTIME, "Alpine") == "Android") {
                arrayOf()
            } else {
                val initHost = localBinDir().child("init-host")
                if (initHost.exists().not()) {
                    initHost.createFileIfNot()
                    initHost.writeText(assets.open("terminal/init-host.sh").bufferedReader().use { it.readText() })
                }

                val init = localBinDir().child("init")
                if (init.exists().not()) {
                    init.createFileIfNot()
                    init.writeText(assets.open("terminal/init.sh").bufferedReader().use { it.readText() })
                }

                arrayOf("-c", initHost.absolutePath)
            }

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