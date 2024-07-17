package com.rk.xededitor.plugin

import android.app.Activity
import android.app.Application
import com.rk.xedplugin.API

class PluginInstance(private val classObj: Class<*>, private val classInstance: Any) : API() {
  
  override fun onLoad(application: Application) {
    Thread { 
      classObj.getMethod("onLoad", Application::class.java).invoke(classInstance, application)
    }.start()
    
  }
  
  override fun onActivityCreate(activity: Activity) {
    Thread { 
      classObj.getMethod("onActivityCreate", Activity::class.java).invoke(classInstance, activity)
    }.start()
    
  }
  
  override fun onActivityDestroy(activity: Activity) {
    Thread { 
      classObj.getMethod("onActivityDestroy", Activity::class.java).invoke(classInstance, activity)
    }.start()
    
  }
  
  override fun onActivityPause(activity: Activity) {
    Thread {
      classObj.getMethod("onActivityPause", Activity::class.java).invoke(classInstance, activity)
    }.start()
    
  }
  
  override fun onActivityResume(activity: Activity) {
    Thread { 
      classObj.getMethod("onActivityResume", Activity::class.java).invoke(classInstance, activity)
    }.start()
    
  }
  
  
}