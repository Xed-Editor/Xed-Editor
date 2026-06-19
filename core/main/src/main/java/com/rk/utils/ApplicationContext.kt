package com.rk.utils

import android.app.Application
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

// legacy - this should have been moved to App.instance but its being used everywhere
var application: Application? = null

val okHttpClient: OkHttpClient by lazy {
    val context = application ?: throw IllegalStateException("Application is not initialized yet")
    OkHttpClient.Builder()
        .cache(
            Cache(
                directory = File(context.cacheDir, "http_cache"),
                maxSize = 10L * 1024L * 1024L // 10 MiB
            )
        )
        .build()
}

