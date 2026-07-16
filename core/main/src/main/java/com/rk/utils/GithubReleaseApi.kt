package com.rk.utils

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@XedExtensionPoint
class GithubReleasesApi(private val owner: String, private val repo: String) {

    private val client = OkHttpClient()

    /**
     * Fetches the tag name of the latest release from the GitHub repository.
     *
     * This function performs a network request to the GitHub REST API. It is a suspending function that executes on the
     * [Dispatchers.IO] scheduler.
     *
     * @return The tag name of the latest release (e.g., "v1.0.0") if successful, or `null` if the request fails, the
     *   response is unsuccessful, or a parsing error occurs.
     */
    @XedExtensionPoint
    suspend fun fetchLatestVersion(): String? {
        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url("https://api.github.com/repos/$owner/$repo/releases/latest").build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null

                    val body = response.body.string()

                    val json = JSONObject(body)
                    json.getString("tag_name")
                }
            }
        }
            .getOrNull()
    }
}
