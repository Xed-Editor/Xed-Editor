package com.rk.xededitor

import android.app.Application
import com.rk.libplugin.Server
import com.rk.xededitor.CrashHandler.CrashHandler

class App : Application() {

  override fun onCreate() {
    super.onCreate()
    CrashHandler.INSTANCE.init(this)
    rkUtils.initUi()
    val server = Server()
    server.start()

  }
}
