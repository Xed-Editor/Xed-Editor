package com.rk.xededitor.terminal

import android.os.Environment
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import java.io.File

object MkSession {
    fun createSession(activity: Terminal): TerminalSession {
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
            
            val workingDir = if (intent.hasExtra("cwd")) {
                intent.getStringExtra("cwd")
            } else if (MainActivity.activityRef.get() != null && ProjectManager.projects.isNotEmpty()) {
                ProjectManager.currentProject.get(MainActivity.activityRef.get()!!).absolutePath.replace(filesDir.absolutePath,"/karbon")
            } else {
                Environment.getExternalStorageDirectory().path
            }
            
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
            )
            
            env.addAll(envVariables.map { "${it.key}=${it.value}" })
            
            
            if (intent.hasExtra("run_cmd")) {
                var shell = "/system/bin/sh"
                val args: Array<String>
                
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
                
                val alpine = intent.getBooleanExtra("alpine", true)
                
                if (alpine) {
                    args = mutableListOf(
                        "-c",
                        File(filesDir.parentFile!!, "proot.sh").absolutePath,
                        intent.getStringExtra("shell")!!,
                    ).also { it.addAll(intent.getStringArrayExtra("args")!!.toList()) }.toTypedArray()
                } else {
                    shell = intent.getStringExtra("shell").toString()
                    args = intent.getStringArrayExtra("args")!!
                }
                
                return TerminalSession(
                    shell,
                    cwd,
                    args,
                    env1,
                    TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                    terminalBackend,
                )
            }
            
            val shell = "/system/bin/sh"
            val args = if (PreferencesData.getBoolean(PreferencesKeys.FAIL_SAFE, false)) {
                arrayOf("")
            } else {
                arrayOf("-c", File(filesDir.parentFile!!, "proot.sh").absolutePath)
            }
            
            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                terminalBackend,
            )
        }
        
    }
}