package com.rk.libPlugin.server

import android.app.Application
import bsh.Interpreter
import com.rk.libPlugin.server.api.API
import com.rk.libPlugin.server.api.PluginLifeCycle
import java.io.File

class Plugin(
  val info: Manifest,
  val pluginHome: String,
  val app: Application
) : Thread() {
  init {
    setDefaultUncaughtExceptionHandler(PluginError)
  }
  
  private lateinit var interpreter: Interpreter
  override fun run() {
    try {
      with(info) {
        interpreter = Interpreter()
        interpreter.setClassLoader(app.classLoader)
        interpreter.set("manifest", info)
        interpreter.set("app", app)
        interpreter.set("api", API.getInstance())
        interpreter.eval(
          """
                import com.rk.xededitor.MainActivity.*;
                import com.rk.xededitor.*;
                import com.rk.libPlugin.*;
                import com.rk.libPlugin.server.*;
                import com.rk.libPlugin.server.api.*;
                import com.rk.libPlugin.server.api.PluginLifeCycle;
                import com.rk.libcommons.*;
                import android.app.Activity;
                import com.jaredrummler.ktsh.Shell;"""
        )
        
        interpreter.source(File(pluginHome, script))
      }
    } catch (e: Exception) {
      PluginError.showError(e)
    }
  }
  
  
}