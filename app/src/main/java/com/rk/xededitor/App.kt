package com.rk.xededitor

import android.app.Application
import com.rk.libPlugin.server.Loader
import com.rk.libcommons.After
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.MainActivity.handlers.VersionChangeHandler
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData

class App : Application() {

 
  override fun onCreate() {
    super.onCreate()

    //create crash handler
    CrashHandler.INSTANCE.init(this).let {
      //initialize shared preferences
      SettingsData.initPref(this).let {
        //handle version change
        //blocking code
        VersionChangeHandler.handle(this)
      }
    }


    //verify if assets are extracted or not
    Assets.verify(this)

    //start plugin server
    After(200){
      if (SettingsData.getBoolean(Keys.ENABLE_PLUGINS,false)){
        val pluginLoader = Loader(this)
        pluginLoader.start()
      }
    }


  }
}
