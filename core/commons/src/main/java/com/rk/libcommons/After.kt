package com.rk.libcommons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Deprecated("Use Coroutine delay() instead")
class After(timeInMillis: Long, runnable: Runnable) {
    var scope: CoroutineScope = GlobalScope

    constructor(
        timeInMillis: Long,
        runnable: Runnable,
        scope: CoroutineScope,
    ) : this(timeInMillis, runnable) {
        this.scope = scope
    }

    init {
        scope.launch(Dispatchers.Default) {
            delay(timeInMillis)
            runnable.run()
        }
    }
}
