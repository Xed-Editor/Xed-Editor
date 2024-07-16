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
    
    Async.run {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityCreate(this@BaseActivity)
        }
      }
      
    }
  }
  
  override fun onDestroy() {
    Async.run {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityDestroy(this@BaseActivity)
        }
      }
    }
    super.onDestroy()
  }
  
  override fun onPause() {
    super.onPause()
    Async.run {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityPause(this@BaseActivity)
        }
      }
    }
  }
  
  override fun onResume() {
    super.onResume()
    
    Async.run {
      val loadedPlugins = PluginServer.loadedPlugins
      if (!loadedPlugins.isNullOrEmpty()) {
        for (pluginInstance in loadedPlugins) {
          pluginInstance.onActivityResume(this@BaseActivity)
        }
      }
    }
  }
  
}