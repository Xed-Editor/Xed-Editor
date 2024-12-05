package com.rk.runner.runners.jvm.jdk

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.isExecPermissionGranted
import com.rk.karbon_exec.isTermuxCompatible
import com.rk.karbon_exec.isTermuxInstalled
import com.rk.karbon_exec.testExecPermission
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.extractAssets
import java.io.File

class JavaRunner(private val type: String) : RunnerImpl {
    override fun run(file: File, context: Context) {
        if (!(isTermuxInstalled() && isExecPermissionGranted() && isTermuxCompatible() && testExecPermission().first)){
            Handler(Looper.getMainLooper()).post { Toast.makeText(context,"Termux-Exec is not enabled", Toast.LENGTH_SHORT).show() }
        }

    }

    override fun getName(): String {
        return type;
    }

    override fun getDescription(): String {
        return when (type) {
            "Java" -> "OpenJDK compiler (single files)"
            "Javac" -> "OpenJDK compiler (multiple files)"
            else -> "OpenJDK compiler + Maven"
        }
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context,drawables.ic_language_java)
    }
    override fun isRunning(): Boolean {
        return false
    }
    override fun stop() {
        TODO("Not yet implemented")
    }
}
