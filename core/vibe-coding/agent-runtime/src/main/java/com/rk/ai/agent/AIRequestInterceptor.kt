package com.rk.ai.agent

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class AIRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}