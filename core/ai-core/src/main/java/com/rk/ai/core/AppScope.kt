package com.rk.ai.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class AppScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default
}
