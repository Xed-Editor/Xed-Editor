package com.rk.xededitor

import android.app.Application
import com.rk.libPlugin.server.Server
import com.rk.xededitor.CrashHandler.CrashHandler

class App : Application() {

  override fun onCreate() {
    super.onCreate()
    rkUtils.initUi()
    CrashHandler.INSTANCE.init(this)

    val pluginServer = Server(this)
    pluginServer.start()



  }
}
