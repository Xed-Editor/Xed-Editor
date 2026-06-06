package com.rk

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object AppDispatchers {
    val IO: CoroutineDispatcher = Dispatchers.IO
    val Main: CoroutineDispatcher = Dispatchers.Main
    val Default: CoroutineDispatcher = Dispatchers.Default
    val Unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
