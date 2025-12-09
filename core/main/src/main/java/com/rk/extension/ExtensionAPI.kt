package com.rk.extension

import android.app.Application

abstract class ExtensionAPI : Application.ActivityLifecycleCallbacks {
    abstract fun onPluginLoaded(extension: Extension)
}
