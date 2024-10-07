package com.rk.xededitor

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.Window
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.enableEdgeToEdge 
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.Insets
import androidx.collection.ArrayMap
import com.rk.libPlugin.server.api.PluginLifeCycle
import com.rk.xededitor.MainActivity.handlers.KeyEventHandler
import com.rk.xededitor.settings.SettingsData
import com.rk.xededitor.ui.theme.ThemeManager
import java.lang.ref.WeakReference
import dev.chrisbanes.insetter.Insetter;

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
  
  fun edgeToEdge(v: View) {
    enableEdgeToEdge()
    Insetter.builder()
      .padding(WindowInsetsCompat.Type.navigationBars())
      .applyToView(v);
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