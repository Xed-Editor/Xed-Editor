package com.rk.extension

import android.app.Application
import androidx.compose.runtime.Composable

abstract class ExtensionAPI(protected val context: ExtensionContext) : Application.ActivityLifecycleCallbacks {
    abstract fun onExtensionLoaded()

    abstract fun onUninstalled()

    open val hasSettings = false

    @Composable open fun SettingsUI() {}
}
