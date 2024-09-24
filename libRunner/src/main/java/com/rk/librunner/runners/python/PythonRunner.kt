package com.rk.librunner.runners.python

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.rk.librunner.RunnerImpl
import java.io.File

class PythonRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        runCommand(
            alpine = true,
            shell = "/bin/bash",
            args = arrayOf("-c", "${context.filesDir.parentFile!!.absolutePath}/rootfs/python.sh $file"),
            workingDir = file.parentFile!!.absolutePath,
            context = context
        )
    }

    fun runCommand(
        //run in alpine or not
        alpine: Boolean,
        //shell or binary to run
        shell: String,
        //arguments passed to shell or binary
        args: Array<String> = arrayOf(),
        //working directory
        workingDir: String,
        //array of environment variables with key value pair eg. HOME=/sdcard,TMP=/tmp
        environmentVars: Array<String>? = arrayOf(),
        //should override default environment variables or not
        overrideEnv: Boolean = false,
        //context to launch terminal activity
        context: Context
    ) {
        context.startActivity(Intent(context, Class.forName("com.rk.xededitor.terminal.Terminal")).also {
            it.putExtra("run_cmd", true)
            it.putExtra("shell", shell)
            it.putExtra("args", args)
            it.putExtra("cwd", workingDir)
            it.putExtra("env", environmentVars)
            it.putExtra("overrideEnv", overrideEnv)
            it.putExtra("alpine", alpine)
        })
    }

    override fun getName(): String {
        return "Python"
    }

    override fun getDescription(): String {
        return "Python compiler"
    }

    override fun getIcon(context: Context): Drawable? {
        return null
    }
}