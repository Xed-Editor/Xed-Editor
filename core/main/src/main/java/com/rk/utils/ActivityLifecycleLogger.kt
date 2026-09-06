package com.rk.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle

object ActivityLifecycleLogger : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        logInfo("${activity::class.java.simpleName}.onCreate")
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        logInfo("${activity::class.java.simpleName}.onResume")
    }

    override fun onActivityPaused(activity: Activity) {
        logInfo("${activity::class.java.simpleName}.onPause")
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        logInfo("${activity::class.java.simpleName}.onDestroy")
    }
}
