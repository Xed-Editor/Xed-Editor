package com.rk.librunner.runners.shell

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.librunner.RunnerImpl
import java.io.File
import com.rk.libcommons.R

class ShellRunner(private val failsafe:Boolean) : RunnerImpl{
  override fun run(file: File, context: Context) {
    context.startActivity(Intent(context,Class.forName("com.rk.xededitor.terminal.Terminal")).also {
      it.putExtra("RUN_CMD",true)
      it.putExtra("failsafe",failsafe)
      it.putExtra("script",file.absolutePath)
    })
  }
  
  override fun getName(): String {
    return if (failsafe){
      "Android Shell"
    }else{
      "Alpine Shell"
    }
  }
  
  override fun getDescription(): String {
    return if (failsafe){
      "/system/bin/sh"
    }else{
      "/bin/sh"
    }
  }
  
  override fun getIcon(context: Context): Drawable? {
    return ContextCompat.getDrawable(context, if (failsafe){
      com.rk.librunner.R.drawable.android
    }else{
      R.drawable.bash
    })
  }
  
}