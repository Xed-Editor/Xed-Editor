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

object PluginRegistry {
    private const val TAG = "PluginRegistry"
    private val cache = mutableMapOf<String, CachedPlugin>()

    suspend fun fetchExtensions() =
        withContext(Dispatchers.IO) {
            val extensions = GitHubApi.fetchContents("plugins")
            extensions
                .filter { it.type == "dir" }
                .mapNotNull { extDir ->
                    val pluginPath = "${extDir.path}/plugin.json"
                    val pluginInfo =
                        try {
                            val pluginFileInfo = GitHubApi.fetchContents(pluginPath).first()

                            val cached = cache[pluginPath]
                            if (cached != null && cached.sha == pluginFileInfo.sha) {
                                return@mapNotNull cached.metadata
                            }

                            val decoded =
                                pluginFileInfo.content?.decodeFromBase64()?.decodeToString()
                                    ?: fetchRawFile(pluginFileInfo)
                                    ?: return@mapNotNull null

                            val pluginInfo = Gson().fromJson(decoded, PluginInfo::class.java)

                            cache[pluginPath] =
                                CachedPlugin(
                                    sha = pluginFileInfo.sha,
                                    metadata = pluginInfo,
                                    lastFetched = System.currentTimeMillis(),
                                )

                            pluginInfo
                        } catch (err: Exception) {
                            Log.w(TAG, "Failed to fetch extension ${extDir.name}: ${err.message}")
                            null
                        }

                    pluginInfo
                }
        }

    private fun fetchRawFile(file: FileContent): String? {
        val url = file.downloadUrl ?: return null
        val request = Request.Builder().url(url).build()
        OkHttpClient().newCall(request).execute().use { response ->
            return if (response.isSuccessful) response.body?.string() else null
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
