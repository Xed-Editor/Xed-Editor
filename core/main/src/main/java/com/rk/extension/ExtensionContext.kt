package com.rk.extension

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import androidx.annotation.Keep
import com.rk.settings.debugOptions.LogCollector
import kotlinx.coroutines.CoroutineScope

@XedExtensionPoint
@Keep
class ExtensionContext(val extension: LocalExtension, val appContext: Context, val scope: CoroutineScope) {
    val settings = SharedPrefExtensionSettings(extension.id)

    val currentActivity
        get() = ActivityProvider.currentActivity

    val appResources
        get() = AppResources(appContext, appContext.resources, appContext.packageName)

    val assets: AssetManager by lazy {
        AssetManager::class.java.getDeclaredConstructor().newInstance().apply {
            val method = javaClass.getMethod("addAssetPath", String::class.java)
            method.invoke(this, extension.apkFile.absolutePath)
        }
    }

    val resources by lazy { Resources(assets, appContext.resources.displayMetrics, appContext.resources.configuration) }

    fun logDebug(msg: String) {
        Log.d(extension.id, msg)
        LogCollector.reportDebug("[${extension.id}]$msg")
    }

    fun logInfo(msg: String) {
        Log.i(extension.id, msg)
        LogCollector.reportInfo("[${extension.id}]$msg")
    }

    fun logWarn(msg: String) {
        Log.w(extension.id, msg)
        LogCollector.reportWarn("[${extension.id}]$msg")
    }

    fun logError(msg: String) {
        Log.e(extension.id, msg)
        LogCollector.reportError("[${extension.id}]$msg")
    }
}
