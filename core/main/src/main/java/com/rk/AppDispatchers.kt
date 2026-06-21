package com.rk

import kotlinx.coroutines.Dispatchers

object AppDispatchers {
    val IO = Dispatchers.IO
    val Main = Dispatchers.Main
    val Default = Dispatchers.Default
    val Unconfined = Dispatchers.Unconfined

    /** Single-threaded executor for heavy CPU-bound startup tasks.
     *  Prevents startup CPU contention that causes main thread ANRs. */
    val Startup = Dispatchers.Default.limitedParallelism(1)
}
