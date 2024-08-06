package com.rk.xededitor

import android.app.Application
import com.rk.xededitor.CrashHandler.CrashHandler
import groovy.lang.Binding
import groovy.lang.GroovyShell


class App : Application() {

  override fun onCreate() {
    super.onCreate()
    CrashHandler.INSTANCE.init(this)
    rkUtils.initUi()

    Thread{

      // Create Groovy script engine
      val binding = Binding()
      binding.setVariable("app", this@App)
      val shell = GroovyShell(binding)

      // Define Groovy script
      val script = "System.out.println(\"hello from groovy\")"


      // Execute script
      shell.evaluate(script)
    }.start()


  }
}