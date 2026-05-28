package com.rk

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object AppDispatchers {
    var IO: CoroutineDispatcher = Dispatchers.IO
    var Main: CoroutineDispatcher = Dispatchers.Main
    var DefaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    var Unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
