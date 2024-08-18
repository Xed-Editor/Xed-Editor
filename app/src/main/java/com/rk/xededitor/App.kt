package com.rk.xededitor

import android.app.Application
import com.rk.libPlugin.server.Server
import com.rk.xededitor.CrashHandler.CrashHandler

class App : Application() {

  companion object{
    private var application:Application? = null
    fun getApplicationInstance() : Application{
      return application!!
    }
  }

  override fun onCreate() {
    application = this
    super.onCreate()

    rkUtils.initUi()

    CrashHandler.INSTANCE.init(this)

    val pluginServer = Server(this)
    pluginServer.start()



  }
}
