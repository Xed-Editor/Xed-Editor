package com.rk.xededitor.ui.screens.settings.terminal

import android.app.Activity
import android.os.Environment
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    fun createSession(activity: Activity,sessionClient: TerminalSessionClient): TerminalSession {
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
            fun getPwd():String{
                return if (intent.hasExtra("cwd")) {
                    intent.getStringExtra("cwd").toString()
                } else if (MainActivity.activityRef.get() != null && ProjectManager.projects.isNotEmpty()) {
                    ProjectManager.currentProject.get(MainActivity.activityRef.get()!!).absolutePath
                } else {
                    Environment.getExternalStorageDirectory().path
                }
            }
            
            val workingDir = getPwd() //.replace(filesDir.parentFile!!.absolutePath,Environment. getExternalStorageDirectory().path)
            
            val tmpDir = File(getTempDir(), "terminal")
            
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
                "LIB_PATH=${applicationContext.applicationInfo.nativeLibraryDir}",
                "LD_LIBRARY_PATH=${File(filesDir.parentFile,"dynamic_libs")}",
                "LINKER=${if (File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}"

            )


            
            env.addAll(envVariables.map { "${it.key}=${it.value}" })
            
            
            if (intent.hasExtra("run_cmd")) {
                
                var cwd = intent.getStringExtra("cwd")
                
                if (cwd!!.isEmpty()) {
                    cwd = workingDir
                }
                
                val env1 = if (intent.getBooleanExtra("overrideEnv", false)) {
                    intent.getStringArrayExtra("env")
                } else {
                    with(mutableListOf<String>()) {
                        addAll(env)
                        addAll(intent.getStringArrayExtra("env")!!.toList())
                        toTypedArray()
                    }
                }
                
                
                val shell: String = intent.getStringExtra("shell").toString()
                val args: Array<String> = intent.getStringArrayExtra("args")!!
                
                return TerminalSession(
                    shell,
                    cwd,
                    args,
                    env1,
                    TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                    sessionClient,
                )
            }
            
            val shell = "/system/bin/sh"
            val args = arrayOf<String>()
            
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