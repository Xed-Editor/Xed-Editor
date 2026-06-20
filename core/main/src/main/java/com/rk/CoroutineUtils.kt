package com.rk

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    onError: ((Throwable) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError?.invoke(throwable) ?: XedLog.e("SafeLaunch", "Coroutine failed: ${throwable.message}", throwable)
    }
    return launch(context + exceptionHandler, start, block)
}

suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.IO, block)

suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Main, block)
