package com.rk.runner.runners.shell

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.karbon_exec.runBashScript
import com.rk.libcommons.TerminalCommand
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import com.rk.settings.Settings
import com.rk.settings.SettingsKey
import java.io.File

class ShellRunner : RunnerImpl {
    override fun run(file: File, context: Context) {

        val runtime = Settings.getString(SettingsKey.TERMINAL_RUNTIME,"Alpine")
        when(runtime){
            "Android" -> {
                launchInternalTerminal(
                    context = context,
                    TerminalCommand(
                        shell = "/system/bin/sh",
                        args = arrayOf("-c",file.absolutePath),
                        id = "shell-android",
                        alpine = false,
                        workingDir = file.parentFile!!.absolutePath
                    )
                )
            }
            "Alpine" -> {
                launchInternalTerminal(
                    context = context,
                    TerminalCommand(
                        shell = "/bin/sh",
                        args = arrayOf(file.absolutePath),
                        id = "shell",
                        alpine = true,
                        workingDir = file.parentFile!!.absolutePath
                    )
                )
            }
            "Termux" -> {
                runBashScript(context, workingDir = file.parentFile!!.absolutePath, script = """
                    bash ${file.absolutePath}
                    echo -e "\n\nProcess completed. Press Enter to go back to Xed-Editor."
                    read
                """.trimIndent())
            }
        }
    }

    override fun getName(): String {
        return Settings.getString(SettingsKey.TERMINAL_RUNTIME,"Alpine")
    }

    override fun getDescription(): String {
        return Settings.getString(SettingsKey.TERMINAL_RUNTIME,"Alpine")
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(
            context,
            drawables.bash,
        )
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}
