package com.rk.projects

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Provides real Minecraft release versions.
 *
 * Primary source is Mojang's official version manifest. If the network is unavailable we fall back
 * to a bundled list of recent releases so the picker is never empty (and never shows fake data).
 */
object MinecraftVersions {

    private const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

    /** Recent releases, newest first. Kept current as a sane offline default. */
    val FALLBACK: List<String> =
        listOf(
            "1.21.4",
            "1.21.3",
            "1.21.1",
            "1.21",
            "1.20.6",
            "1.20.4",
            "1.20.2",
            "1.20.1",
            "1.19.4",
            "1.19.2",
            "1.18.2",
            "1.16.5",
            "1.12.2",
        )

    /**
     * Fetches release versions from Mojang (releases only, newest first). Returns [FALLBACK] on any
     * failure. Safe to call from a coroutine; performs IO on [Dispatchers.IO].
     */
    suspend fun fetchReleases(limit: Int = 60): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                    val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection)
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.requestMethod = "GET"

                    val body =
                        connection.inputStream.use { it.bufferedReader().readText() }.also {
                            connection.disconnect()
                        }

                    val versions = JSONObject(body).getJSONArray("versions")
                    val releases = ArrayList<String>(limit)
                    var i = 0
                    while (i < versions.length() && releases.size < limit) {
                        val obj = versions.getJSONObject(i)
                        if (obj.optString("type") == "release") {
                            releases.add(obj.getString("id"))
                        }
                        i++
                    }
                    if (releases.isEmpty()) FALLBACK else releases
                }
                .getOrDefault(FALLBACK)
        }
}
