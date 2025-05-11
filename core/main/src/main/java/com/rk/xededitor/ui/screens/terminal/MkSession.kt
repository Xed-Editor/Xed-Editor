package com.rk.xededitor.ui.screens.terminal

import android.os.Environment
import com.rk.compose.filetree.currentProject
import com.rk.file_wrapper.FileWrapper
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localLibDir
import com.rk.libcommons.pendingCommand
import com.rk.settings.Settings
import com.rk.App.Companion.getTempDir
import com.rk.xededitor.BuildConfig
//import com.rk.xededitor.MainActivity.file.ProjectManager
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
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE"),
                "PATH" to System.getenv("PATH")
            )

            fun getPwd() = if (intent.hasExtra("cwd")){
                intent.getStringExtra("cwd").toString()
            }else if (currentProject != null){
                if (currentProject is FileWrapper){
                    currentProject!!.getAbsolutePath()
                }else{
                    //toast("Current project ${currentProject?.getName()} is not a native directory")
                    Environment.getExternalStorageDirectory().path
                }
            }else{
                Environment.getExternalStorageDirectory().path
            }

            val workingDir = (pendingCommand?.workingDir ?: getPwd())

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
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "HOME=${alpineHomeDir()}",
                "PROMPT_DIRTRIM=2",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}"
            )


            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            setupTerminalFiles()

            val initHost = localBinDir().child("init-host")

            val args: Array<String>

            val shell = if (pendingCommand == null) {
                args = if (Settings.terminal_runtime == "Android"
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