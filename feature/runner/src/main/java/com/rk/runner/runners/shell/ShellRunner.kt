package com.rk.runner.runners.shell

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.isExecPermissionGranted
import com.rk.karbon_exec.isTermuxCompatible
import com.rk.karbon_exec.isTermuxInstalled
import com.rk.karbon_exec.runBashScript
import com.rk.karbon_exec.testExecPermission
import com.rk.libcommons.R
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import java.io.File

class ShellRunner(private val failsafe: Boolean) : RunnerImpl {
    
    override fun run(file: File, context: Context) {
        if (failsafe){
            fun runCommand(
                // shell or binary to run
                shell: String,
                // arguments passed to shell or binary
                args: Array<String> = arrayOf(),
                // working directory leave empty for default
                workingDir: String = "",
                // environment variables with key value pair eg HOME=/sdcard,TMP=/tmp
                environmentVars: Array<String>? = arrayOf(),
                // should override default environment variables or not
                overrideEnv: Boolean = false,
                // context to launch terminal activity
                context: Context,
            ) {
                context.startActivity(Intent(context, Class.forName("com.rk.xededitor.terminal.Terminal")).also {
                    it.putExtra("run_cmd", true)
                    it.putExtra("shell", shell)
                    it.putExtra("args", args)
                    it.putExtra("cwd", workingDir)
                    it.putExtra("env", environmentVars)
                    it.putExtra("overrideEnv", overrideEnv)
                })
            }
            
            
            
            runCommand(shell = "/system/bin/sh", args = arrayOf("-c",file.absolutePath), context = context)
            return
        }
        if (!(isTermuxInstalled() && isExecPermissionGranted() && isTermuxCompatible() && testExecPermission().first)){
            Handler(Looper.getMainLooper()).post { Toast.makeText(context,"Termux-Exec is not enabled", Toast.LENGTH_SHORT).show() }
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
