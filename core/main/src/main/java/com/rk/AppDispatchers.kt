package com.rk

import kotlinx.coroutines.Dispatchers

object AppDispatchers {
    val IO = Dispatchers.IO
    val Main = Dispatchers.Main
    val Default = Dispatchers.Default
    val Unconfined = Dispatchers.Unconfined
}
