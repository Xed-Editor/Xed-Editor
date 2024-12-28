package com.rk.runner.runners.python

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.localBinDir
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import java.io.File

class PythonRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        val py = File(context.localBinDir(), "python")
        if (py.exists().not()) {
            py.writeText(context.assets.open("terminal/python.sh").bufferedReader()
                .use { it.readText() })
        }
        launchInternalTerminal(
            context = context, TerminalCommand(
                shell = "/bin/sh",
                args = arrayOf(py.absolutePath,file.absolutePath),
                id = "python",
                workingDir = file.parentFile!!.absolutePath
            )
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
