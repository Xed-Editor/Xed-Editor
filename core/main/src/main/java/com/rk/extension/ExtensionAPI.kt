package com.rk.extension


abstract class ExtensionAPI {
    abstract fun onPluginLoaded(extension: Extension)
    abstract fun onMainActivityCreated()
    abstract fun onMainActivityPaused()
    abstract fun onMainActivityResumed()
    abstract fun onMainActivityDestroyed()
    abstract fun onLowMemory()
}
