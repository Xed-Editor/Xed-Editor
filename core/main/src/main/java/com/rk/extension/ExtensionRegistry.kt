package com.rk.extension

import android.util.Log
import com.rk.utils.errorDialog
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.json.JSONObject

private val json = Json { ignoreUnknownKeys = true }

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private val cache = mutableMapOf<String, CachedExtension>()

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchExtensions(): List<ExtensionManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                    val url = URL("https://xed-editor.app/api/extensions")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, response)
                    val extensions = JSONObject(response).getJSONArray("extensions")

                    val list = mutableListOf<ExtensionManifest>()

                    for (i in 0 until extensions.length()) {
                        val obj = extensions.getJSONObject(i)
                        val manifest = obj.getJSONObject("manifest")

                        val name = manifest.getString("name")
                        val id = manifest.getString("id")
                        val version = manifest.getString("version")
                        val mainClass = manifest.getString("mainClass")
                        val description = manifest.getString("description")
                        val authors = manifest.getJSONArray("authors")
                        val targetAppVersion = manifest.getInt("targetAppVersion")
                        val repository = manifest.getString("repository")
                        val license = manifest.getString("license")

                        val extensionAuthors = mutableListOf<ExtensionAuthor>()

                        for (j in 0 until authors.length()) {
                            val author = authors.getString(j)
                            extensionAuthors.add(ExtensionAuthor(displayName = author, github = null))
                        }

                        list.add(
                            ExtensionManifest(
                                id,
                                name,
                                mainClass = mainClass,
                                version = version,
                                description = description,
                                authors = extensionAuthors,
                                minAppVersion = -1,
                                targetAppVersion = targetAppVersion,
                                repository = repository,
                                license = license,
                                tags = emptyList(),
                            )
                        )
                    }

                    return@withContext list
                }
                .onFailure {
                    it.printStackTrace()
                    errorDialog(it)
                }

            return@withContext emptyList<ExtensionManifest>()
        }

    suspend fun getIconUrl(manifest: ExtensionManifest): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                    val url = URL("https://xed-editor.app/api/extensions/${manifest.id}")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, response)
                    val iconUrl = JSONObject(response).getJSONObject("download").getString("icon")
                    return@withContext iconUrl
                }
                .onFailure {
                    it.printStackTrace()
                    errorDialog(it)
                }
            return@withContext null
        }

    suspend fun getReadmeUrl(manifest: ExtensionManifest): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                    val url = URL("https://xed-editor.app/api/extensions/${manifest.id}")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, response)
                    val iconUrl = JSONObject(response).getJSONObject("download").getString("readme")
                    return@withContext iconUrl
                }
                .onFailure {
                    it.printStackTrace()
                    errorDialog(it)
                }
            return@withContext null
        }
}
