package com.rk.settings.extension

import com.rk.DefaultScope
import com.rk.extension.Extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ExtensionAPIManager : ExtensionAPI(), CoroutineScope by DefaultScope {
    private val mutex = Mutex()

    override fun onMainActivityPaused() {
        launch { mutex.withLock { loadedExtensions.values.forEach(ExtensionAPI::onMainActivityPaused) } }
    }

    override fun onMainActivityResumed() {
        launch { mutex.withLock { loadedExtensions.values.forEach(ExtensionAPI::onMainActivityResumed) } }
    }

    override fun onMainActivityDestroyed() {
        launch { mutex.withLock { loadedExtensions.values.forEach(ExtensionAPI::onMainActivityDestroyed) } }
    }

    override fun onLowMemory() {
        launch { mutex.withLock { loadedExtensions.values.forEach(ExtensionAPI::onLowMemory) } }
    }

    @Deprecated("Use onPluginLoaded function instead", replaceWith = ReplaceWith("onPluginLoaded(extension, isInit)"))
    override fun onMainActivityCreated() {
        launch { mutex.withLock { loadedExtensions.values.forEach(ExtensionAPI::onMainActivityCreated) } }
    }

    override fun onPluginLoaded(extension: Extension, isInit: Boolean) {
        launch { mutex.withLock { loadedExtensions.values.forEach { it.onPluginLoaded(extension, isInit) } } }
    }

    @Deprecated(
        "Use onPluginLoaded(extension: Extension, isInit: Boolean) instead.",
        replaceWith = ReplaceWith("onPluginLoaded(extension, isInit)"),
    )
    override fun onPluginLoaded(extension: Extension) {
        launch { mutex.withLock { loadedExtensions.values.forEach { it.onPluginLoaded(extension) } } }
    }
}
