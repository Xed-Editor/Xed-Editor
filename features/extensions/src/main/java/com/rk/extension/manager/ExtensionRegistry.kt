package com.rk.extension.manager

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.rk.extension.EXTENSION_API_BASE
import com.rk.extension.ExtensionManifest
import com.rk.extension.ExtensionStats
import com.rk.extension.ICONPACKS_API_BASE
import com.rk.extension.THEMES_API_BASE
import com.rk.icons.pack.IconPackManifest
import com.rk.settings.extension.InstallState
import com.rk.utils.errorDialog
import com.rk.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable private data class ExtensionListResponse(val extensions: List<ExtensionEntry>)

@Serializable private data class ExtensionEntry(val manifest: ExtensionManifest)

@Serializable private data class ExtensionDetail(val downloads: Int? = null, val download: DownloadUrls)

@Serializable
private data class DownloadUrls(val icon: String? = null, val readme: String? = null, val zip: String, val size: Int)

@Serializable
data class ThemeStoreEntry(
    val id: String,
    val userId: String,
    val manifest: ThemeManifest,
)

@Serializable
data class ThemeManifest(
    val id: String,
    val name: String,
)

@Serializable data class ThemesResponse(val themes: List<ThemeStoreEntry>)

@Serializable
data class IconPackStoreEntry(
    val id: String,
    val userId: String,
    val manifest: IconPackManifest,
)

@Serializable data class IconPacksResponse(val iconPacks: List<IconPackStoreEntry>)

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private const val BASE_URL = EXTENSION_API_BASE

    private val client: OkHttpClient = okHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    val downloadProgress = mutableStateMapOf<String, Float>()
    val activeInstalls = mutableStateMapOf<String, InstallState>()

    suspend fun downloadFileWithProgress(
        url: String,
        destFile: File,
        onProgress: (progress: Float) -> Unit,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val totalBytes = response.body.contentLength()
                        destFile.parentFile?.mkdirs()
                        response.body.byteStream().use { input ->
                            destFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytesRead = 0L
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    if (totalBytes > 0) {
                                        val progress = totalBytesRead.toFloat() / totalBytes
                                        onProgress(progress)
                                    } else {
                                        onProgress(-1f)
                                    }
                                }
                            }
                        }
                    }
                    true
                }
                .onFailure {
                    it.printStackTrace()
                }
                .getOrElse { false }
        }

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
                    val jsonString = requestJson(THEMES_API_BASE)
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
                    val jsonString = requestJson(ICONPACKS_API_BASE)
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
                    val zipUrl = "${ICONPACKS_API_BASE}/$id/iconpack.zip"
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
