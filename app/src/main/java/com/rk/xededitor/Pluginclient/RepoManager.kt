package com.rk.xededitor.Pluginclient

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

object RepoManager {

    private fun fetch(url: String): String? {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                response.body?.string()
            } else null
        }
    }

    private suspend fun fetchBitmapFromUrl(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()

            val request = Request.Builder().url(url).build()

            // Execute the request and get the response
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Get the InputStream of the response body
                    val inputStream: InputStream? = response.body?.byteStream()

                    // Decode the InputStream into a Bitmap
                    inputStream?.let {
                        BitmapFactory.decodeStream(it)
                    }
                } else null
            }
        }
    }

    private fun getRawGithubUrl(url: String): String {
        return url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
    }

    fun getPluginsCallback(actionOnLoadPlugin: (PluginItem) -> Unit) {

        GlobalScope.launch(Dispatchers.IO) {
            val url =
                getRawGithubUrl("https://github.com/RohitKushvaha01/Karbon-Plugins/blob/main/repo.json")
            println(url)

            val jsonStr = fetch(url)
            if (jsonStr == null) {
                println("fetch request returned null")
                return@launch
            }


            val json = JSONObject(jsonStr)

            withContext(Dispatchers.Default) {
                try {
                    val pluginsArray: JSONArray = json.getJSONArray("plugins")
                    for (i in 0 until pluginsArray.length()) {
                        val pluginUrl = getRawGithubUrl(pluginsArray.getString(i))
                        val manifestUrl = fetch("$pluginUrl/main/manifest.json")
                        val manifestJson = JSONObject(manifestUrl)
                        val name = manifestJson.getString("name")
                        val packageName = manifestJson.getString("packageName")
                        val description = manifestJson.getString("description")
                        // val author = manifestJson.getString("author")
                        // val version = manifestJson.getString("version")
                        val versionCode = manifestJson.getInt("versionCode")

                        val icon = manifestJson.getString("icon")
                        val iconUrl = "$pluginUrl/main/$icon"

                        val iconBitmap = fetchBitmapFromUrl(iconUrl)

                        val pluginItem =
                            PluginItem(iconBitmap, name, packageName, description, versionCode)

                        withContext(Dispatchers.Main) {
                            actionOnLoadPlugin(pluginItem)
                        }


                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }


            }
        }
    }
}