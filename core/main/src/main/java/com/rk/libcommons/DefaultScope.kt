package com.rk.libcommons

import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

//same as MainActivity.lifeCycleScope
@OptIn(DelicateCoroutinesApi::class)
val DefaultScope:CoroutineScope
    get() {
        return MainActivity.activityRef.get()?.lifecycleScope ?: GlobalScope
    }
