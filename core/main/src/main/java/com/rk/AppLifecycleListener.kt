package com.rk

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


var isAppForeground = true
    private set

class AppLifecycleListener : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        isAppForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppForeground = false
    }
}
