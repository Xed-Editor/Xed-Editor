package com.rk.libPlugin.server

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PluginUtils {
    private val InstalledPlugins = ArrayList<Plugin>()
    fun Application.indexPlugins() {

        InstalledPlugins.clear()
        val root = getPluginRoot()
        val pluginsFiles = root.listFiles()
        if (root.exists().not() and (pluginsFiles.isNullOrEmpty())) {
            return
        }

        for (plugin in pluginsFiles!!) {
            val manifestFile = File(plugin, "manifest.json")

            var manifest: Manifest? = null
            try {
                val json = manifestFile.readText()
                val gson = Gson()
                manifest = gson.fromJson(json, Manifest::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "PluginError ${e.message}", Toast.LENGTH_LONG).show()
                }
                
            }

            InstalledPlugins.add(Plugin(manifest!!, plugin.absolutePath, this))
        }
    }

   

    
    fun getInstalledPlugins(): List<Plugin> {
        return synchronized(InstalledPlugins) { InstalledPlugins }
    }

    
    fun Context.getPluginRoot(): File {
        return File(filesDir.parentFile, "plugins")
    }

    
    fun isPluginActive(context: Context, packageName: String, default: Boolean): Boolean {
        val sharedPreferences =
            context.applicationContext.getSharedPreferences("PluginPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(packageName, default)
    }

    
    fun setPluginActive(context: Context, packageName: String, active: Boolean) {
        val sharedPreferences =
            context.applicationContext.getSharedPreferences("PluginPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(packageName, active).apply()
    }

}