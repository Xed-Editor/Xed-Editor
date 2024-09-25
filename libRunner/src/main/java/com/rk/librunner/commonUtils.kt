package com.rk.librunner

import android.content.Context
import android.content.Intent

object commonUtils {
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
}