// DO NOT UPDATE PACKAGE NAME OTHERWISE EXTENSIONS WILL BREAK
package com.rk.extension

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.annotation.Keep
import com.rk.extension.api.XedExtensionPoint
import com.rk.extension.api.logDebug
import com.rk.extension.api.logError
import com.rk.extension.api.logInfo
import com.rk.extension.api.logWarn
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

    fun logDebug(msg: String) = extension.id.logDebug(msg)

    fun logInfo(msg: String) = extension.id.logInfo(msg)

    fun logWarn(msg: String) = extension.id.logWarn(msg)

    fun logError(msg: String) = extension.id.logError(msg)
}
