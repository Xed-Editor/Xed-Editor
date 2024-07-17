package com.rk.xededitor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.rk.xededitor.plugin.PluginServer

private val handler: Handler = Handler(Looper.getMainLooper())

fun runOnUi(runnable: Runnable?) {
  handler.post(runnable!!)
}

abstract class BaseActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
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