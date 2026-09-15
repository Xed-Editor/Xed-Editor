package com.rk.account

import com.rk.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AccountApiException(message: String, val status: Int? = null) : Exception(message)

internal object XedAccountClient {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun begin(codeChallenge: String): AuthState =
        execute(
            Request.Builder()
                .url("${AccountConfig.API_BASE}/auth/android/begin")
                .post(jsonBody("codeChallenge" to codeChallenge))
                .build()
        )
            .let { accountJson.decodeFromString<AuthState>(it) }

    suspend fun exchange(code: String, codeVerifier: String): TokenResponse =
        execute(
            Request.Builder()
                .url("${AccountConfig.API_BASE}/auth/android/token")
                .post(jsonBody("code" to code, "codeVerifier" to codeVerifier))
                .build()
        )
            .let { accountJson.decodeFromString<TokenResponse>(it) }

    suspend fun refresh(refreshToken: String): TokenResponse =
        execute(
            Request.Builder()
                .url("${AccountConfig.API_BASE}/auth/android/refresh")
                .post(jsonBody("refreshToken" to refreshToken))
                .build()
        )
            .let { accountJson.decodeFromString<TokenResponse>(it) }

    suspend fun logout(accessToken: String) {
        execute(
            Request.Builder()
                .url("${AccountConfig.API_BASE}/auth/android/logout")
                .header("Authorization", "Bearer $accessToken")
                .post(jsonBody())
                .build()
        )
    }

    private fun jsonBody(vararg pairs: Pair<String, String>) =
        buildJsonObject { pairs.forEach { (key, value) -> put(key, value) } }
            .toString()
            .toRequestBody(JSON)

    private suspend fun execute(request: Request): String =
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message =
                        runCatching { accountJson.decodeFromString<ApiError>(body).error }.getOrNull()
                            ?: "Request failed (HTTP ${response.code})"
                    throw AccountApiException(message, response.code)
                }
                body
            }
        }
}
