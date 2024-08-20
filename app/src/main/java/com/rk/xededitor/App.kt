package com.rk.xededitor

import android.app.Application
import com.rk.libPlugin.server.Server
import com.rk.libcommons.After
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.Settings.SettingsData

class App : Application() {

 
  override fun onCreate() {
    super.onCreate()
    
    //initialize uiHandler
    rkUtils.initUi()

    //initialize shared preferences
    SettingsData.initPref(this)
    
    //verify if assets are extracted or not
    Assets.verify(this)

    //create crash handler
    CrashHandler.INSTANCE.init(this)


    //start plugin server
    After(200){
      if (SettingsData.getBoolean(SettingsData.Keys.ENABLE_PLUGINS,false)){
        val pluginServer = Server(this)
        pluginServer.start()
      }
    }


  }
}
