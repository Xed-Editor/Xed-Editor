package com.rk.runner.runners.python

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.rk.runner.commonUtils.extractAssets
import java.io.File

class PythonRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        if (!(isTermuxInstalled() && isExecPermissionGranted() && isTermuxCompatible() && testExecPermission().first)){
            Handler(Looper.getMainLooper()).post { Toast.makeText(context,"Termux-Exec is not enabled", Toast.LENGTH_SHORT).show() }
            return
        }
        
        runBashScript(
            context, script = """
                cd ${file.parentFile.absolutePath}
                
                if command -v python >/dev/null 2>&1; then
                    echo "python version: ${'$'}(python --version)"
                    python ${file.absolutePath}
                else
                    echo "Python is not installed, Installing..."
                    pkg i python -y
                    python ${file.absolutePath}
                fi
                
                
                echo "Process Completed (Press Enter to exit)"
                read -r
                
                """, background = false
        )

        if (isTermuxRunning().not()) {
            Handler(Looper.getMainLooper()).post {
                MaterialAlertDialogBuilder(context).apply {
                    setTitle("Launch Termux?")
                    setMessage("Termux is stopped so karbon is unable to run command in it. do you like to start it?")
                    setPositiveButton("Launch", { dialog, which ->
                        launchTermux()
                        application!!.startActivity(Intent(application!!, context::class.java))
                    })
                    setNegativeButton("Cancel", { dialog, which -> })
                    show()
                }
            }
        }
    }

    override fun getName(): String {
        return "Python"
    }

    override fun getDescription(): String {
        return "Python compiler"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, drawables.ic_language_python)
    }
    override fun isRunning(): Boolean {
        return false
    }
    override fun stop() {
        TODO("Not yet implemented")
    }
}
