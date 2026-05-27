package com.rk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A base Activity that provides common functionality and safe coroutine launching.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        XedLog.d(this::class.java.simpleName, "onCreate")
    }

    protected fun safeLaunch(
        context: CoroutineContext = EmptyCoroutineContext,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return lifecycleScope.safeLaunch(context, onError = onError, block = block)
    }
}
