package com.rk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.extension.ExtensionManager
import com.rk.icons.pack.IconPackManager
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.*

/**
 * Foundation utilities for the application.
 */

/**
 * Provides centralized access to application-wide managers and current context.
 */
object XedManager {
    lateinit var app: Application
        private set

    private var _currentActivity = WeakReference<Activity?>(null)
    val currentActivity: Activity?
        get() = _currentActivity.get()

    val extensionManager: ExtensionManager by lazy { ExtensionManager(app) }
    val iconPackManager: IconPackManager by lazy { IconPackManager(app) }

    fun init(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { _currentActivity = WeakReference(activity) }
            override fun onActivityStarted(activity: Activity) { _currentActivity = WeakReference(activity) }
            override fun onActivityResumed(activity: Activity) { _currentActivity = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) { if (currentActivity == activity) _currentActivity.clear() }
        })
    }
}

/**
 * Extension to access the application instance safely.
 */
val android.content.Context.app: android.app.Application
    get() = applicationContext as android.app.Application

/**
 * Centralized logger for the application.
 */
object XedLog {
    fun d(tag: String, msg: String) = Log.d("XED_$tag", msg)
    fun i(tag: String, msg: String) = Log.i("XED_$tag", msg)
    fun w(tag: String, msg: String) = Log.w("XED_$tag", msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) = Log.e("XED_$tag", msg, throwable)
}

/**
 * A central provider for coroutine dispatchers to allow for easier testing and consistency.
 */
object AppDispatchers {
    var IO: CoroutineDispatcher = Dispatchers.IO
    var Main: CoroutineDispatcher = Dispatchers.Main
    var DefaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    var Unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

/**
 * A base ViewModel that provides safe coroutine launching.
 */
abstract class BaseViewModel : ViewModel() {
    protected fun safeLaunch(
        context: CoroutineContext = EmptyCoroutineContext,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.safeLaunch(context, onError = onError, block = block)
    }
}

/**
 * Safely launches a coroutine with built-in error logging.
 */
fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    onError: ((Throwable) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        XedLog.e("SafeLaunch", "Coroutine failed: ${throwable.message}", throwable)
        onError?.invoke(throwable)
    }
    return launch(context + exceptionHandler, start, block)
}

/**
 * Extension to switch to IO dispatcher using the central provider.
 */
suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T {
    return withContext(AppDispatchers.IO, block)
}

/**
 * Extension to switch to Main dispatcher using the central provider.
 */
suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T {
    return withContext(AppDispatchers.Main, block)
}
