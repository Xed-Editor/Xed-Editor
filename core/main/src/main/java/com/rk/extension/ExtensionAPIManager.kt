package com.rk.extension

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.rk.App
import com.rk.DefaultScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ExtensionAPIManager : Application.ActivityLifecycleCallbacks, CoroutineScope by DefaultScope {
    private val mutex = Mutex()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        launch {
            mutex.withLock {
                launch {
                    App.extensionManager.loadedExtensions.values.forEach {
                        it?.onActivityCreated(activity, savedInstanceState)
                    }
                }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        launch {
            mutex.withLock {
                launch { App.extensionManager.loadedExtensions.values.forEach { it?.onActivityDestroyed(activity) } }
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        launch {
            mutex.withLock {
                launch { App.extensionManager.loadedExtensions.values.forEach { it?.onActivityPaused(activity) } }
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        launch {
            mutex.withLock {
                launch { App.extensionManager.loadedExtensions.values.forEach { it?.onActivityResumed(activity) } }
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        launch {
            mutex.withLock {
                launch {
                    App.extensionManager.loadedExtensions.values.forEach {
                        it?.onActivitySaveInstanceState(activity, outState)
                    }
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        launch {
            mutex.withLock {
                launch { App.extensionManager.loadedExtensions.values.forEach { it?.onActivityStarted(activity) } }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        launch {
            mutex.withLock {
                launch { App.extensionManager.loadedExtensions.values.forEach { it?.onActivityStopped(activity) } }
            }
        }
    }
}
