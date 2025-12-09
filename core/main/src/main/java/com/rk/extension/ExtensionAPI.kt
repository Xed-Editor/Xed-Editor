package com.rk.extension

import android.app.Application

abstract class ExtensionAPI : Application.ActivityLifecycleCallbacks {
    open fun onPluginLoaded(extension: Extension) {}
}