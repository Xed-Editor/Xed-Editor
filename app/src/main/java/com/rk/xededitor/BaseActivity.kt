package com.rk.xededitor

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.ArrayMap
import androidx.core.view.WindowInsetsCompat
import com.rk.libPlugin.server.api.PluginLifeCycle
import com.rk.xededitor.MainActivity.handlers.KeyEventHandler
import com.rk.xededitor.ui.theme.ThemeManager
import java.lang.ref.WeakReference

abstract class BaseActivity : AppCompatActivity() {

  companion object {
    val activityMap = ArrayMap<Class<out BaseActivity>,WeakReference<Activity>>()
    
    //used by plugins
    fun getActivity(clazz:Class<out BaseActivity>):Activity?{
      return activityMap[clazz]?.get()
    }
    
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (event != null) {
      KeyEventHandler.onAppKeyEvent(event)
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    ThemeManager.apply(this)
    super.onCreate(savedInstanceState)
    activityMap[javaClass] = WeakReference(this)
    PluginLifeCycle.onActivityEvent(this,PluginLifeCycle.LifeCycleType.CREATE)
  }
  
  override fun onResume() {
    super.onResume()
    PluginLifeCycle.onActivityEvent(this,PluginLifeCycle.LifeCycleType.RESUMED)
  }
  

  override fun onPause() {
    super.onPause()
    ThemeManager.apply(this)
    PluginLifeCycle.onActivityEvent(this,PluginLifeCycle.LifeCycleType.PAUSED)
  }
  
  override fun onDestroy() {
    super.onDestroy()
    PluginLifeCycle.onActivityEvent(this,PluginLifeCycle.LifeCycleType.DESTROY)
  }
  
}