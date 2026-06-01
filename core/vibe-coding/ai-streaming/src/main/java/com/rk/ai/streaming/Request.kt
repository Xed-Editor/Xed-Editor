package com.rk.ai.streaming

import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody
import java.io.IOException
import kotlin.coroutines.resumeWithException

fun Request.Builder.configureReferHeaders(url: String): Request.Builder {
    val httpUrl = url.toHttpUrlOrNull() ?: return this
    return when (httpUrl.host) {
        "aihubmix.com" -> {
            addHeader("APP-Code", "DKHA9468")
        }

        "openrouter.ai" -> {
            this
                .addHeader("X-Title", "RikkaHub")
                .addHeader("HTTP-Referer", "https://rikka-ai.com")
        }

        else -> this
    }
}

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

fun ResponseBody?.stringSafe(): String? {
    return when (this) {
        is RealResponseBody -> string()
        else -> null
    }
}

suspend fun Call.await(): okhttp3.Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                continuation.resume(response) { cause, _, _ ->
                    response.close()
                }
            }
        })
    }
}
