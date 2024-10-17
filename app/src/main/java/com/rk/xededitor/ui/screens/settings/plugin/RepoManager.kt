package com.rk.xededitor.ui.screens.settings.plugin

import com.rk.plugin.server.PluginInfo
import com.rk.libcommons.DefaultScope
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import com.rk.xededitor.rkUtils.runOnUiThread
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

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
    
    private fun getRawGithubUrl(url: String): String {
        return url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
    }
    
    fun getPluginsCallback(actionOnLoadPlugins: (List<PluginInfo>) -> Unit) {
        DefaultScope.launch(Dispatchers.IO) {
            try {
                val url = getRawGithubUrl(
                    "https://github.com/RohitKushvaha01/Karbon-Plugins/blob/main/repo.json"
                )
                
                val jsonStr = fetch(url)
                if (jsonStr == null) {
                    rkUtils.toast(getString(R.string.fetch_failed))
                    return@launch
                }
                
                val json = JSONObject(jsonStr)
                
                withContext(Dispatchers.Default) {
                    val plugins = mutableListOf<PluginInfo>()
                    try {
                        val pluginsArray: JSONArray = json.getJSONArray("plugins")
                        for (i in 0 until pluginsArray.length()) {
                            try {
                                val pluginUrl = getRawGithubUrl(pluginsArray.getString(i))
                                val manifestUrl = fetch("$pluginUrl/main/manifest.json")
                                if (manifestUrl.isNullOrEmpty()) {
                                    continue
                                }
                                val manifestJson = JSONObject(manifestUrl)
                                val name = manifestJson.getString("name")
                                val packageName = manifestJson.getString("packageName")
                                val description = manifestJson.getString("description")
                                val author = manifestJson.getString("author")
                                val version = manifestJson.getString("version")
                                val versionCode = manifestJson.getInt("versionCode")
                                
                                val icon = manifestJson.getString("icon")
                                val iconUrl = "$pluginUrl/main/$icon"
                                
                                val pluginInfo = PluginInfo(
                                    icon = iconUrl,
                                    title = name,
                                    packageName = packageName,
                                    description = description,
                                    repo = pluginsArray.getString(i),
                                    author = author,
                                    version = version,
                                    versionCode = versionCode,
                                    script = null,
                                    isLocal = false,
                                )
                                
                                plugins.add(pluginInfo)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                continue
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        actionOnLoadPlugins.invoke(plugins)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    rkUtils.toast(e.message)
                }
            }
        }
        
    }
}
