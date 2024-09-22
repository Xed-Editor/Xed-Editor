package com.rk.xededitor.pluginClient

import com.rk.xededitor.rkUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object RepoManager {
  
  fun fetch(url: String): String? {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    
    client.newCall(request).execute().use { response ->
      return if (response.isSuccessful) {
        response.body?.string()
      } else null
    }
  }
  

   fun getRawGithubUrl(url: String): String {
    return url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
  }
  
  @OptIn(DelicateCoroutinesApi::class)
  fun getPluginsCallback(actionOnLoadPlugins: (List<PluginItem>) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
      val url =
        getRawGithubUrl("https://github.com/RohitKushvaha01/Karbon-Plugins/blob/main/repo.json")
      
      val jsonStr = fetch(url)
      if (jsonStr == null) {
        rkUtils.toast("fetch request returned null")
        return@launch
      }
      
      
      val json = JSONObject(jsonStr)
      
      
      withContext(Dispatchers.Default) {
        val plugins = mutableListOf<PluginItem>()
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
              // val author = manifestJson.getString("author")
              // val version = manifestJson.getString("version")
              val versionCode = manifestJson.getInt("versionCode")
              
              val icon = manifestJson.getString("icon")
              val iconUrl = "$pluginUrl/main/$icon"
              
              //val iconBitmap = fetchBitmapFromUrl(iconUrl)
              
              val pluginItem = PluginItem(
                icon,
                name,
                packageName,
                description,
                pluginsArray.getString(i)
              )
              
              plugins.add(pluginItem)
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
    }
  }
}