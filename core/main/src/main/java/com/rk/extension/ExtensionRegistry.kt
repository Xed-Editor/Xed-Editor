package com.rk.extension

import android.util.Log
import com.rk.utils.errorDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable private data class ExtensionListResponse(val extensions: List<ExtensionEntry>)

@Serializable private data class ExtensionEntry(val manifest: ExtensionManifest)

@Serializable private data class ExtensionDetail(val downloads: Int? = null, val download: DownloadUrls)

@Serializable private data class DownloadUrls(val icon: String? = null, val readme: String? = null, val zip: String)

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private const val BASE_URL = "https://xed-editor.app/api/extensions"

    private val client: OkHttpClient = OkHttpClient()
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

    suspend fun getDownloadCount(id: String): Int? = getDetails(id)?.downloads

    private suspend fun getDetails(id: String): ExtensionDetail? =
        runCatching {
                withContext(Dispatchers.IO) {
                    val jsonString = requestJson("$BASE_URL/$id")
                    json.decodeFromString<ExtensionDetail>(jsonString)
                }
            }
            .getOrNull()

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
                    val zipUrl = getDetails(manifest.id)?.download?.zip ?: error("Extension ZIP file was not found.")

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
                    errorDialog(it)
                }
                .getOrElse { false }
        }
}
