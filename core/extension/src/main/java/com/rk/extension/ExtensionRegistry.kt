package com.rk.extension

import android.util.Log
import com.rk.extension.github.FileContent
import com.rk.extension.github.GitHubApi
import com.rk.extension.github.decodeFromBase64
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val json = Json { ignoreUnknownKeys = true }

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private val cache = mutableMapOf<String, CachedExtension>()

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchExtensions() =
        withContext(Dispatchers.IO) {
            val extensions = GitHubApi.fetchContents("extensions")
            extensions
                .filter { it.type == "dir" }
                .mapNotNull { extDir ->
                    val extensionPath = "${extDir.path}/extension.json"
                    val extensionInfo =
                        try {
                            val extensionFileInfo = GitHubApi.fetchContents(extensionPath).first()

                            val cached = cache[extensionPath]
                            if (cached != null && cached.sha == extensionFileInfo.sha) {
                                return@mapNotNull cached.metadata
                            }

                            val decoded =
                                extensionFileInfo.content?.decodeFromBase64()?.decodeToString()
                                    ?: fetchRawFile(extensionFileInfo)
                                    ?: return@mapNotNull null

                            val extensionManifest =
                                runCatching { json.decodeFromString<ExtensionManifest>(decoded) }
                                    .getOrElse { e ->
                                        when (e) {
                                            is MissingFieldException ->
                                                Log.w(
                                                    TAG,
                                                    "Manifest for ${extDir.name} is missing required fields: ${e.missingFields}",
                                                )
                                            else ->
                                                Log.w(TAG, "Failed to parse manifest for ${extDir.name}: ${e.message}")
                                        }
                                        return@mapNotNull null
                                    }

                            cache[extensionPath] =
                                CachedExtension(
                                    sha = extensionFileInfo.sha,
                                    metadata = extensionManifest,
                                    lastFetched = System.currentTimeMillis(),
                                )

                            extensionManifest
                        } catch (err: Exception) {
                            Log.w(TAG, "Failed to fetch extension ${extDir.name}: ${err.message}")
                            null
                        }

                    extensionInfo
                }
        }

    private fun fetchRawFile(file: FileContent): String? {
        val url = file.downloadUrl ?: return null
        val request = Request.Builder().url(url).build()
        OkHttpClient().newCall(request).execute().use { response ->
            return if (response.isSuccessful) response.body.string() else null
        }
    }

    suspend fun downloadExtension(id: ExtensionId, targetDir: File) =
        withContext(Dispatchers.IO) {
            val contents = GitHubApi.fetchContents("extensions/$id")

            contents.forEach { item ->
                when (item.type) {
                    "file" -> GitHubApi.downloadFile(item, targetDir)
                    "dir" -> GitHubApi.downloadDir(item.path, File(targetDir, item.name))
                }
            }
        }

    fun getIconUrl(manifest: ExtensionManifest) = manifest.icon?.let { GitHubApi.getRawUrl("${manifest.id}/$it") }

    fun getReadmeUrl(manifest: ExtensionManifest) = GitHubApi.getRawUrl("${manifest.id}/README.md")

    fun getChangelogUrl(manifest: ExtensionManifest) = GitHubApi.getRawUrl("${manifest.id}/CHANGELOG.md")

    suspend fun calcSize(manifest: ExtensionManifest): Long {
        return GitHubApi.fetchContents("extensions/${manifest.id}").sumOf { it.size.toLong() }
    }
}
