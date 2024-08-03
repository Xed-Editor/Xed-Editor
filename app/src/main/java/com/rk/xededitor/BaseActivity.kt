package com.rk.xededitor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.plugin.PluginServer

private val handler: Handler = Handler(Looper.getMainLooper())

fun runOnUi(runnable: Runnable?) {
  handler.post(runnable!!)
}

abstract class BaseActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    if (!SettingsData.isDarkMode(this)) {
      //light mode
      window.navigationBarColor = Color.parseColor("#FEF7FF")
      val decorView = window.decorView
      var flags = decorView.systemUiVisibility
      flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      decorView.systemUiVisibility = flags
    }else{
val window = window
window.statusBarColor = Color.parseColor("#141118")

}
    if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
      val window = window
      window.navigationBarColor = Color.BLACK
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.statusBarColor = Color.BLACK
    }
    
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityCreate(this@BaseActivity)
        }
      }
      
    }.start()
  }
  
  
  override fun onDestroy() {
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityDestroy(this@BaseActivity)
        }
      }
    }.start()
    super.onDestroy()
  }
  
  override fun onPause() {
    super.onPause()
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityPause(this@BaseActivity)
        }
      }
    }.start()
  }
  
  override fun onResume() {
    super.onResume()
    
    Thread {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityResume(this@BaseActivity)
        }
      }
    }.start()
  }
  
}

class ActivityAboutUs : BaseActivity() {
}