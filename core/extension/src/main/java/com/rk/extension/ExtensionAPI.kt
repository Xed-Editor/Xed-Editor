package com.rk.extension


abstract class ExtensionAPI {
    abstract fun onPluginLoaded()
    abstract fun onAppCreated()
    abstract fun onAppLaunched()
    abstract fun onAppPaused()
    abstract fun onAppResumed()
    abstract fun onAppDestroyed()
    abstract fun onLowMemory()
}
