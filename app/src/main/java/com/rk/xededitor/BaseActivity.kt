package com.rk.xededitor

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge 
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.ArrayMap
import com.rk.libPlugin.server.api.PluginLifeCycle
import com.rk.xededitor.Settings.SettingsData
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

  override fun onCreate(savedInstanceState: Bundle?) {
    ThemeManager.apply(this)
    super.onCreate(savedInstanceState)
    activityMap[javaClass] = WeakReference(this)
    PluginLifeCycle.onActivityEvent(this,PluginLifeCycle.LifeCycleType.CREATE)
  }
  
  fun edgeToEdge() {
    //enableEdgeToEdge()
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