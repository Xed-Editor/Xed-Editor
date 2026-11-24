package com.rk.extension.store

import android.util.Log
import com.rk.extension.model.ExtensionManifest
import com.rk.packages.model.ReviewStats
import com.rk.utils.logError
import com.rk.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client of the extension registry.
 *
 * Extensions are the only package type whose registry access lives in this feature; themes and icon packs are served by
 * `com.rk.packages.StoreManager` in `:core:main`, so this class (and the whole feature) can be dropped from a build
 * without affecting them.
 */
object ExtensionStoreApi {
    private const val TAG = "ExtensionStoreApi"

    const val EXTENSION_API_BASE = "https://xed-editor.app/api/extensions"

    private val client: OkHttpClient = okHttpClient

    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    suspend fun fetchExtensions(): List<ExtensionEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = requestJson(EXTENSION_API_BASE)
                json.decodeFromString<ExtensionListResponse>(jsonString).extensions
            }
                .onFailure { logError(it) }
                .getOrElse { emptyList() }
        }

    fun getIconUrl(id: String): String = "$EXTENSION_API_BASE/$id/icon.png"

    fun getReadmeUrl(id: String): String = "$EXTENSION_API_BASE/$id/README.md"

    fun getChangelogUrl(id: String): String = "$EXTENSION_API_BASE/$id/CHANGELOG.md"

    fun getPluginDownloadUrl(id: String): String = "$EXTENSION_API_BASE/$id/plugin.zip"

    private fun requestJson(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val body = res.body.string()
            Log.d(TAG, body)
            body
        }
    }
}

@Serializable private data class ExtensionListResponse(val extensions: List<ExtensionEntry>)

@Serializable
data class ExtensionEntry(
    val id: String,
    val manifest: ExtensionManifest,
    val downloads: Int? = null,
    val download: DownloadUrls,
    val size: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val rating: ReviewStats,
)

@Serializable
data class DownloadUrls(
    val icon: String? = null,
    val readme: String? = null,
    val zip: String,
    val size: Long? = null,
)
