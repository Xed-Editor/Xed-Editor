package com.rk.runner.runners.python

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.runner.R
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.exctractAssets
import com.rk.runner.commonUtils.runCommand
import java.io.File

class PythonRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        exctractAssets(context){
            runCommand(
                alpine = true,
                shell = "/karbon/rootfs/python.sh",
                args = arrayOf(file.name),
                workingDir = file.parentFile!!.absolutePath,
                context = context
            )
        }
    }

    override fun getName(): String {
        return "Python"
    }

    override fun getDescription(): String {
        return "Python compiler"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, com.rk.libcommons.R.drawable.ic_language_python)
    }
}