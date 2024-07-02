package com.rk.xededitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.plugin.PluginServer

abstract class BaseActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Thread {
      SettingsData.applyPrefs(this)
      val loadedPlugins = PluginServer.loadedPlugins
      while (loadedPlugins.isNullOrEmpty()) {
        Thread.sleep(50)
      }
      for (pluginInstance in loadedPlugins) {
        pluginInstance.onActivityCreate(this)
      }
    }.start()
  }
  
  override fun onDestroy() {
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      while (loadedPlugins.isNullOrEmpty()) {
        Thread.sleep(50)
      }
      for (pluginInstance in loadedPlugins) {
        pluginInstance.onActivityDestroy(this)
      }
    }.start()
    super.onDestroy()
  }
  
  override fun onPause() {
    super.onPause()
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      while (loadedPlugins.isNullOrEmpty()) {
        Thread.sleep(50)
      }
      for (pluginInstance in loadedPlugins) {
        pluginInstance.onActivityPause(this)
      }
    }.start()
  }
  
  override fun onResume() {
    super.onResume()
    
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      while (loadedPlugins.isNullOrEmpty()) {
        Thread.sleep(50)
      }
      for (pluginInstance in loadedPlugins) {
        pluginInstance.onActivityResume(this)
      }
    }.start()
  }
  
}