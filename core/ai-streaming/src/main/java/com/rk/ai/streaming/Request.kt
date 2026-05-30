package com.rk.ai.streaming

import okhttp3.Request
import android.util.Base64

fun Request.Builder.configureReferHeaders(proxies: Map<String, String>): Request.Builder {
    proxies.forEach { (key, value) ->
        if (key.startsWith("header:")) {
            header(key.removePrefix("header:"), value)
        }
    }
    return this
}

fun String.encodeBase64(): String {
    return Base64.encodeToString(this.toByteArray(), Base64.NO_WRAP)
}
