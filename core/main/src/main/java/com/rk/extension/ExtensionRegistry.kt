package com.rk.extension

import android.util.Log
import com.rk.utils.application
import com.rk.utils.errorDialog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ExtensionRegistry {
    private const val TAG = "ExtensionRegistry"
    private const val BASE_URL = "https://xed-editor.app/api/extensions"
    private val client: OkHttpClient = OkHttpClient()
    // ---------------- CACHE ----------------
    private var extensionsCache: List<ExtensionManifest>? = null
    private val detailCache = ConcurrentHashMap<String, JSONObject>()

    // prevents duplicate simultaneous requests
    private val inflight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<JSONObject>>()

    // ---------------- CORE ----------------

    suspend fun fetchExtensions(force: Boolean): List<ExtensionManifest> =
        withContext(Dispatchers.IO) {
            extensionsCache
                ?.takeIf { !force }
                ?.let {
                    return@withContext it
                }

            runCatching {
                    val json = requestJson(BASE_URL)

                    val extensions = json.getJSONArray("extensions")
                    val list = mutableListOf<ExtensionManifest>()

                    for (i in 0 until extensions.length()) {
                        val obj = extensions.getJSONObject(i)
                        val manifest = obj.getJSONObject("manifest")

                        list.add(
                            ExtensionManifest(
                                id = manifest.getString("id"),
                                name = manifest.getString("name"),
                                mainClass = manifest.getString("mainClass"),
                                version = manifest.getString("version"),
                                description = manifest.getString("description"),
                                authors =
                                    manifest.getJSONArray("authors").let { arr ->
                                        List(arr.length()) { j -> ExtensionAuthor(arr.getString(j), null) }
                                    },
                                minAppVersion = -1,
                                targetAppVersion = manifest.getInt("targetAppVersion"),
                                repository = manifest.getString("repository"),
                                license = manifest.optString("license", ""),
                                tags = emptyList(),
                            )
                        )
                    }

                    extensionsCache = list
                    list
                }
                .onFailure {
                    it.printStackTrace()
                    errorDialog(it)
                }
                .getOrElse { emptyList() }
        }

    // ---------------- DETAILS (cached + shared) ----------------

    private suspend fun getDetails(id: String): JSONObject = coroutineScope {

        // 1. memory cache
        detailCache[id]?.let {
            return@coroutineScope it
        }

        // 2. avoid duplicate simultaneous requests
        inflight[id]?.let {
            return@coroutineScope it.await()
        }

        val deferred = async(Dispatchers.IO) { requestJson("$BASE_URL/$id") }

        inflight[id] = deferred

        try {
            val result = deferred.await()
            detailCache[id] = result
            result
        } finally {
            inflight.remove(id)
        }
    }

    // ---------------- PUBLIC APIs ----------------

    suspend fun getIconUrl(manifest: ExtensionManifest): String? =
        runCatching { getDetails(manifest.id).getJSONObject("download").getString("icon") }.getOrNull()

    suspend fun getReadmeFile(manifest: ExtensionManifest): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                    val id = manifest.id

                    // cache file location
                    val dir = File(application!!.cacheDir, "readme_cache")
                    if (!dir.exists()) dir.mkdirs()

                    val file = File(dir, "$id.md")

                    // ✅ return if already cached
                    if (file.exists() && file.length() > 0) {
                        return@withContext file.absolutePath
                    }

                    // 1. get readme URL from API
                    val readmeUrl = getDetails(id).getJSONObject("download").getString("readme")

                    // 2. download content
                    val request = Request.Builder().url(readmeUrl).build()

                    client.newCall(request).execute().use { res ->
                        if (!res.isSuccessful) error("HTTP ${res.code}")

                        val body = res.body?.string() ?: error("Empty readme")

                        // 3. write to file
                        file.writeText(body)
                    }

                    return@withContext file.absolutePath
                }
                .onFailure {
                    it.printStackTrace()
                    errorDialog(it)
                }

            return@withContext null
        }

    private suspend fun getReadmeUrl(manifest: ExtensionManifest): String? =
        runCatching { getDetails(manifest.id).getJSONObject("download").getString("readme") }.getOrNull()

    suspend fun getDownloadCount(manifest: ExtensionManifest): Int? =
        runCatching { getDetails(manifest.id).getInt("downloads") }.getOrElse { null }

    // ---------------- HTTP ----------------

    private fun requestJson(url: String): JSONObject {
        val req = Request.Builder().url(url).build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val body = res.body?.string() ?: error("Empty response")
            Log.d(TAG, body)
            return JSONObject(body)
        }
    }
}
