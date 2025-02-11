package com.rk.libcommons

import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

//same as MainActivity.lifeCycleScope

val DefaultScope:CoroutineScope
    get() {
        return MainActivity.activityRef.get()?.lifecycleScope ?: GlobalScope
    }
