package com.rk.runner.runners.shell

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.karbon_exec.askLaunchTermux
import com.rk.karbon_exec.isExecPermissionGranted
import com.rk.karbon_exec.isTermuxCompatible
import com.rk.karbon_exec.isTermuxInstalled
import com.rk.karbon_exec.isTermuxRunning
import com.rk.karbon_exec.launchTermux
import com.rk.karbon_exec.runBashScript
import com.rk.karbon_exec.testExecPermission
import com.rk.libcommons.application
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.File

class ShellRunner(private val failsafe: Boolean) : RunnerImpl {

    @OptIn(DelicateCoroutinesApi::class)
    override fun run(file: File, context: Context) {
        if (failsafe) {
            return
        }

        if (!(isTermuxInstalled() && isExecPermissionGranted() && isTermuxCompatible() && testExecPermission().first)) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Termux-Exec is not enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        runBashScript(
            context, script = """
                cd ${file.parentFile.absolutePath}
                
                bash -c ${file.absolutePath}
                
                echo "Process Completed (Press Enter to exit)"
                read -r
                
                """, background = false
        )

        if (isTermuxRunning().not()) {
            askLaunchTermux(context)
        }


    }

    override fun getName(): String {
        return if (failsafe) {
            "Android Shell"
        } else {
            "Termux"
        }
    }

    override fun getDescription(): String {
        return if (failsafe) {
            "Android"
        } else {
            "Termux"
        }
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(
            context,
            if (failsafe) {
                drawables.android
            } else {
                drawables.bash
            },
        )
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}
