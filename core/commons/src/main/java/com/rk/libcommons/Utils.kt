package com.rk.libcommons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
fun postIO(block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(Dispatchers.IO, block = block)
}