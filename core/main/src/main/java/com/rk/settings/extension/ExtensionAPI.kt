package com.rk.settings.extension

import com.rk.extension.Extension

abstract class ExtensionAPI {
    open fun onPluginLoaded(extension: Extension, isInit: Boolean) {}

    open fun onMainActivityPaused() {}

    open fun onMainActivityResumed() {}

    open fun onMainActivityDestroyed() {}

    open fun onLowMemory() {}

    @Deprecated(
        message = "Use onPluginLoaded(extension: Extension, isInit: Boolean) instead.",
        replaceWith = ReplaceWith("onPluginLoaded(extension, isInit)"),
    )
    open fun onPluginLoaded(extension: Extension) {}

    @Deprecated(
        message = "Use onPluginLoaded function instead",
        replaceWith = ReplaceWith("onPluginLoaded(extension, isInit)"),
    )
    open fun onMainActivityCreated() {}
}
