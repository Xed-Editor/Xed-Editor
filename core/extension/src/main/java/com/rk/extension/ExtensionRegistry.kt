package com.rk.extension

import android.util.Log
import com.google.gson.Gson
import com.rk.extension.github.FileContent
import com.rk.extension.github.GitHubApi
import com.rk.extension.github.decodeFromBase64
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private val cache = mutableMapOf<String, CachedExtension>()

    suspend fun fetchExtensions() =
        withContext(Dispatchers.IO) {
            val extensions = GitHubApi.fetchContents("plugins")
            extensions
                .filter { it.type == "dir" }
                .mapNotNull { extDir ->
                    val extensionPath = "${extDir.path}/plugin.json"
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

                            val extensionInfo = Gson().fromJson(decoded, ExtensionInfo::class.java)

                            cache[extensionPath] =
                                CachedExtension(
                                    sha = extensionFileInfo.sha,
                                    metadata = extensionInfo,
                                    lastFetched = System.currentTimeMillis(),
                                )

                            extensionInfo
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
            val contents = GitHubApi.fetchContents("plugins/$id")

            contents.forEach { item ->
                when (item.type) {
                    "file" -> GitHubApi.downloadFile(item, targetDir)
                    "dir" -> GitHubApi.downloadDir(item.path, File(targetDir, item.name))
                }
            }
        }
}
