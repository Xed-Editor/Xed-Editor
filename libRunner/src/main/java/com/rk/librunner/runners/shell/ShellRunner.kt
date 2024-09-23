package com.rk.librunner.runners.shell

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.libcommons.R
import com.rk.librunner.RunnerImpl
import java.io.File

class ShellRunner(private val failsafe: Boolean) : RunnerImpl {
  override fun run(file: File, context: Context) {
    if (failsafe) {
      runCommand(
        alpine = false,
        shell = "/system/bin/sh",
        args = arrayOf("-c",file.absolutePath),
        context = context
      )
    }else{
      runCommand(
        alpine = true,
        shell = "/bin/sh",
        args = arrayOf("-c",file.absolutePath),
        context = context
      )
    }

  }


  fun runCommand(
    //run in alpine or not
    alpine: Boolean,
    //shell or binary to run
    shell: String,
    //arguments passed to shell or binary
    args: Array<String> = arrayOf(),
    //working directory leave empty for default
    workingDir: String = "",
    //environment variables with key value pair eg HOME=/sdcard,TMP=/tmp
    environmentVars: Array<String>? = arrayOf(),
    //should override default environment variables or not
    overrideEnv: Boolean = false,
    //context to launch terminal activity
    context: Context
  ) {
    context.startActivity(Intent(
      context, Class.forName("com.rk.xededitor.terminal.Terminal")
    ).also {
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
    return if (failsafe) {
      "Android Shell"
    } else {
      "Alpine Shell"
    }
  }

  override fun getDescription(): String {
    return if (failsafe) {
      "/system/bin/sh"
    } else {
      "/bin/sh"
    }
  }

  override fun getIcon(context: Context): Drawable? {
    return ContextCompat.getDrawable(
      context, if (failsafe) {
        com.rk.librunner.R.drawable.android
      } else {
        R.drawable.bash
      }
    )
  }

}