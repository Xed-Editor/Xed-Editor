package com.rk.xededitor.plugin

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo

class PluginInstance(val appinfo:ApplicationInfo, val classObj:Class<*>, val classInstance:Any) : API{
    override fun onLoad(application: Application?) {
        classObj.getMethod("onLoad", Application::class.java).invoke(classInstance,application)
    }

    override fun onActivityCreate(activity: Activity) {
        classObj.getMethod("onActivityCreate",  Activity::class.java).invoke(classInstance,activity)
    }


}