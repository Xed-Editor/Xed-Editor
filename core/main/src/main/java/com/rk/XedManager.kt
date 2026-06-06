package com.rk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.rk.extension.ExtensionManager
import com.rk.icons.pack.IconPackManager
import java.lang.ref.WeakReference

object XedManager {
    lateinit var app: Application
        private set

    private var _currentActivity = WeakReference<Activity?>(null)
    val currentActivity: Activity?
        get() = _currentActivity.get()

    val extensionManager: ExtensionManager by lazy { ExtensionManager(app) }
    val iconPackManager: IconPackManager by lazy { IconPackManager(app) }

    fun init(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { _currentActivity = WeakReference(activity) }
            override fun onActivityStarted(activity: Activity) { _currentActivity = WeakReference(activity) }
            override fun onActivityResumed(activity: Activity) { _currentActivity = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (_currentActivity.get() == activity) {
                    _currentActivity.clear()
                }
            }
        })
    }
}

val android.content.Context.app: android.app.Application
    get() = applicationContext as android.app.Application
