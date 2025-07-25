package com.rk.extension


abstract class ExtensionAPI {
    abstract fun onPluginLoaded(extension: Extension)

    @Deprecated("Use onPluginLoaded function instead")
    open fun onMainActivityCreated(){}
    open fun onMainActivityPaused(){}
    open fun onMainActivityResumed(){}
    open fun onMainActivityDestroyed(){}
    open fun onLowMemory() {}
}
