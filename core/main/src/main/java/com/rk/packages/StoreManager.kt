package com.rk.packages

import com.rk.common.PackageType
import com.rk.icons.pack.IconPackEntry
import com.rk.theme.ThemeEntry
import com.rk.utils.logError
import com.rk.utils.okHttpClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val THEMES_API_BASE = "https://xed-editor.app/api/themes"
private const val ICONPACKS_API_BASE = "https://xed-editor.app/api/icon-packs"
private const val REVIEWS_API_BASE = "https://xed-editor.app/api/reviews"

@Serializable private data class ThemeListResponse(val themes: List<ThemeEntry>)

@Serializable private data class IconPackListResponse(val iconPacks: List<IconPackEntry>)

/**
 * Package-type agnostic half of the store client.
 *
 * Owns the remote registry access for themes and icon packs plus the download/install bookkeeping shared by every store
 * section. Extension-specific registry access lives with the extensions feature (see
 * `com.rk.extension.store.ExtensionStoreApi`), so this class stays usable when that feature is not bundled.
 */
object StoreManager {
    private val client: OkHttpClient = okHttpClient

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    private val _activeInstalls = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val activeInstalls: StateFlow<Map<String, InstallState>> = _activeInstalls.asStateFlow()

    fun setActiveInstall(id: String, state: InstallState) {
        _activeInstalls.update { it + (id to state) }
    }

    fun setDownloadProgress(id: String, progress: Float) {
        _downloadProgress.update { it + (id to progress) }
    }

    fun clearInstall(id: String) {
        _activeInstalls.update { it - id }
        _downloadProgress.update { it - id }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

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
                .onFailure { logError(it) }
                .getOrElse { false }
        }

    fun themeDownloadUrl(id: String): String = "$THEMES_API_BASE/$id/theme.xed"

    fun iconPackDownloadUrl(id: String): String = "$ICONPACKS_API_BASE/$id/iconpack.zip"

    fun getThemeIconUrl(id: String): String = "$THEMES_API_BASE/$id/icon.png"

    fun getThemeReadmeUrl(id: String): String = "$THEMES_API_BASE/$id/README.md"

    fun getThemeChangelogUrl(id: String): String = "$THEMES_API_BASE/$id/CHANGELOG.md"

    fun getIconPackIconUrl(id: String): String = "$ICONPACKS_API_BASE/$id/icon.png"

    fun getIconPackReadmeUrl(id: String): String = "$ICONPACKS_API_BASE/$id/README.md"

    fun getIconPackChangelogUrl(id: String): String = "$ICONPACKS_API_BASE/$id/CHANGELOG.md"

    /** URL of the reviews endpoint for one package, e.g. `.../reviews?packageId=foo&packageType=theme`. */
    fun reviewsUrl(type: PackageType, id: String): HttpUrl =
        REVIEWS_API_BASE.toHttpUrl()
            .newBuilder()
            .addQueryParameter("packageId", id)
            .addQueryParameter("packageType", type.value)
            .build()

    private fun requestJson(url: String): String {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            res.body.string()
        }
    }

    suspend fun fetchThemes(): List<ThemeEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = requestJson(THEMES_API_BASE)
                json.decodeFromString<ThemeListResponse>(jsonString).themes
            }
                .onFailure { logError(it) }
                .getOrElse { emptyList() }
        }

    suspend fun fetchIconPacks(): List<IconPackEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = requestJson(ICONPACKS_API_BASE)
                json.decodeFromString<IconPackListResponse>(jsonString).iconPacks
            }
                .onFailure { logError(it) }
                .getOrElse { emptyList() }
        }
}
