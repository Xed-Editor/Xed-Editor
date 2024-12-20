package com.rk.runner.runners.python

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
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.karbon_exec.launchTermux
import com.rk.karbon_exec.runBashScript
import com.rk.karbon_exec.testExecPermission
import com.rk.libcommons.application
import com.rk.libcommons.localBinDir
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.extractAssets
import java.io.File

class PythonRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        val py = File(context.localBinDir(),"python")
        if (py.exists().not()){
            py.writeText(context.assets.open("terminal/python.sh").bufferedReader().use { it.readText() })
        }
        launchInternalTerminal(
            context = context,
            shell = "/bin/sh",
            arrayOf("-c",py.absolutePath),
            id = "python",
            workingDir = file.parentFile.absolutePath
        )
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
