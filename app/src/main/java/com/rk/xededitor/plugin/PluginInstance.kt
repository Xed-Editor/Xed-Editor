package com.rk.xededitor.plugin

import android.app.Activity
import android.app.Application
import com.rk.xededitor.Async
import com.rk.xedplugin.API

class PluginInstance(private val classObj: Class<*>, private val classInstance: Any) : API() {
  
  override fun onLoad(application: Application) {
    Async.run { 
      classObj.getMethod("onLoad", Application::class.java).invoke(classInstance, application)
    } 
    
  }
  
  override fun onActivityCreate(activity: Activity) {
    Async.run { 
      classObj.getMethod("onActivityCreate", Activity::class.java).invoke(classInstance, activity)
    } 
    
  }
  
  override fun onActivityDestroy(activity: Activity) {
    Async.run { 
      classObj.getMethod("onActivityDestroy", Activity::class.java).invoke(classInstance, activity)
    } 
    
  }
  
  override fun onActivityPause(activity: Activity) {
    Async.run {
      classObj.getMethod("onActivityPause", Activity::class.java).invoke(classInstance, activity)
    } 
    
  }
  
  override fun onActivityResume(activity: Activity) {
    Async.run { 
      classObj.getMethod("onActivityResume", Activity::class.java).invoke(classInstance, activity)
    } 
    
  }
  
  
}