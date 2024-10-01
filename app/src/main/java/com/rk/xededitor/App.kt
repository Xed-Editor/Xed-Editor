package com.rk.xededitor

import android.app.Application
import android.content.Context

import androidx.appcompat.app.AppCompatDelegate

import com.rk.libPlugin.server.Loader
import com.rk.libcommons.After
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.MainActivity.handlers.VersionChangeHandler
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.TerminalSettings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {
  
  companion object{
    lateinit var app: Application
    fun Context.getTempDir():File{
      val tmp = File(filesDir.parentFile,"tmp")
      if (!tmp.exists()) {
        tmp.mkdir()
      }
      return tmp
    }
  }
 
  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    app = this
    super.onCreate()

    //create temp folder
    GlobalScope.launch(Dispatchers.IO){
      val tmp = File(filesDir.parentFile,"tmp")
      if (!tmp.exists()){
        tmp.mkdir()
      }else{
        tmp.deleteRecursively()
        tmp.mkdir()
      }
    }

    //create crash handler
    CrashHandler.INSTANCE.init(this).let {
      //initialize shared preferences
      SettingsData.initPref(this).let {
        //handle version change
        //blocking code
        VersionChangeHandler.handle(this)
        
        val settingDefaultNightMode = SettingsData.getString(
          Keys.DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()
        ).toInt()

        if (settingDefaultNightMode != AppCompatDelegate.getDefaultNightMode()) {
          AppCompatDelegate.setDefaultNightMode(settingDefaultNightMode)
        }
      }
    }
    
    //start plugin loader
    After(200){
      if (SettingsData.getBoolean(Keys.ENABLE_PLUGINS,false)){
        val pluginLoader = Loader(this)
        pluginLoader.start()
      }
    }
    
    SetupEditor.init(this)
    TerminalSettings.updateProotArgs(this)

  }
}
