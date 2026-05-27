package com.rk

import com.rk.AppDispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * An application-wide CoroutineScope that provides structured concurrency.
 * This should be used instead of [kotlinx.coroutines.GlobalScope] to ensure
 * that application-level coroutines can be properly managed and cancelled if needed.
 */
object AppScope : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = AppDispatchers.DefaultDispatcher + job

    /**
     * Cancels all coroutines running in this scope.
     * Should only be called when the application is terminating.
     */
    fun cancelAll() {
        job.cancel()
    }
}
