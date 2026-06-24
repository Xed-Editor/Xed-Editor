package com.rk.extension.manager

import android.util.Log
import com.rk.XedConstants
import com.rk.extension.ExtensionManifest
import com.rk.extension.ExtensionStats
import com.rk.utils.errorDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import com.rk.utils.okHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.GsonBuilder
import com.rk.theme.ThemeConfig
import com.rk.icons.pack.IconPackManifest

@Serializable private data class ExtensionListResponse(val extensions: List<ExtensionEntry>)

@Serializable private data class ExtensionEntry(val manifest: ExtensionManifest)

@Serializable private data class ExtensionDetail(val downloads: Int? = null, val download: DownloadUrls)

@Serializable
private data class DownloadUrls(val icon: String? = null, val readme: String? = null, val zip: String, val size: Int)

@Serializable
data class ThemeStoreEntry(
    val id: String,
    val userId: String,
    val manifest: JsonObject
)

@Serializable
data class ThemesResponse(val themes: List<ThemeStoreEntry>)

@Serializable
data class IconPackStoreEntry(
    val id: String,
    val userId: String,
    val manifest: IconPackManifest
)

@Serializable
data class IconPacksResponse(val iconPacks: List<IconPackStoreEntry>)

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private const val BASE_URL = XedConstants.EXTENSION_API_BASE

    private val client: OkHttpClient = okHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchExtensions(): List<ExtensionManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                    val jsonString = requestJson(BASE_URL)
                    val response = json.decodeFromString<ExtensionListResponse>(jsonString)
                    response.extensions.map { it.manifest }
                }
                .onFailure {
                    it.printStackTrace()
                    throw it
                }
                .getOrElse { emptyList() }
        }

    fun getIconUrl(id: String): String = "$BASE_URL/$id/icon.png"

    fun getReadmeUrl(id: String): String = "$BASE_URL/$id/README.md"

    fun getChangelogUrl(id: String): String = "$BASE_URL/$id/CHANGELOG.md"

    suspend fun getStats(id: String): ExtensionStats {
        val details =
            runCatching {
                    withContext(Dispatchers.IO) {
                        val jsonString = requestJson("$BASE_URL/$id")
                        json.decodeFromString<ExtensionDetail>(jsonString)
                    }
                }
                .getOrNull()

        return ExtensionStats(
            downloadCount = details?.downloads,
            rating = null,
            size = details?.download?.size?.toLong(),
        )
    }

    private fun requestJson(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val body = res.body.string()
            Log.d(TAG, body)
            body
        }
    }

    suspend fun downloadZip(manifest: ExtensionManifest, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    val zipUrl = "$BASE_URL/${manifest.id}/plugin.zip"

                    val request = Request.Builder().url(zipUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        destFile.parentFile?.mkdirs()
                        response.body.byteStream().use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    true
                }
                .onFailure {
                    it.printStackTrace()
                    errorDialog(throwable = it)
                }
                .getOrElse { false }
        }

    suspend fun fetchThemes(): List<ThemeStoreEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = requestJson("https://xed-editor.app/api/themes")
                val response = json.decodeFromString<ThemesResponse>(jsonString)
                response.themes
            }
            .onFailure {
                it.printStackTrace()
            }
            .getOrElse { emptyList() }
        }

    suspend fun fetchIconPacks(): List<IconPackStoreEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = requestJson("https://xed-editor.app/api/icon-packs")
                val response = json.decodeFromString<IconPacksResponse>(jsonString)
                response.iconPacks
            }
            .onFailure {
                it.printStackTrace()
            }
            .getOrElse { emptyList() }
        }

    suspend fun downloadIconPackZip(id: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val zipUrl = "https://xed-editor.app/api/icon-packs/$id/iconpack.zip"
                val request = Request.Builder().url(zipUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    destFile.parentFile?.mkdirs()
                    response.body.byteStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                true
            }
            .onFailure {
                it.printStackTrace()
                errorDialog(throwable = it)
            }
            .getOrElse { false }
        }
}
