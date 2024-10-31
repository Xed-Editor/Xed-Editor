package com.rk.runner.runners.jvm.jdk

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.runner.R
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.exctractAssets
import com.rk.runner.commonUtils.runCommand
import java.io.File

class JavacRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        exctractAssets(context) {
            runCommand(
                alpine = true,
                shell = "/karbon/rootfs/java.sh",
                args = arrayOf("javac", file.absolutePath),
                workingDir = file.parentFile!!.absolutePath,
                context = context,
            )
        }
    }

    override fun getName(): String {
        return "Javac"
    }

    override fun getDescription(): String {
        return "OpenJDK compiler"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, com.rk.libcommons.R.drawable.ic_language_java)
    }
    override fun isRunning(): Boolean {
        return false
    }
    override fun stop() {
        TODO("Not yet implemented")
    }
}
