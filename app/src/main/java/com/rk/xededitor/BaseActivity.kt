package com.rk.xededitor

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Build
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
  
  fun edgeToEdge(window: Window, view: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insetsController = window.insetsController
        insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    } else {
        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = true
    }

    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(
            systemBarsInsets.left,
            systemBarsInsets.top,
            systemBarsInsets.right,
            systemBarsInsets.bottom
        )
        insets
    }

    view.requestApplyInsets()
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