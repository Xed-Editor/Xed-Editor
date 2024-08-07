package com.rk.libPlugin

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File


class Server(val app: Application) : Thread() {

    override fun run() {
        app.indexPlugins()
        InstalledPlugins.forEach { plugin: Plugin ->
            if (isPluginActive(app,plugin.info.packageName,false)){
                plugin.start()
            }
        }
    }

    private fun Application.indexPlugins(){
        val root = getPluginRoot()
        if (!root.exists()){
            println("no plugins directory exiting..")
        }
        val pluginsFile = root.listFiles()
        if (pluginsFile == null || pluginsFile.isEmpty()) {
            return
        }
        for (plugin in pluginsFile) {
            val manifestFile = File(plugin, "manifest.json")
            if (!manifestFile.exists()) {
                Log.e(
                    "Plugin Server",
                    "can't index plugin ${plugin.name}; no manifest.json file found"
                )
                continue
            }

            var manifest: Manifest? = null
            try {
                val json = manifestFile.readText()
                val gson = Gson()
                manifest = gson.fromJson(json, Manifest::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("Plugin Server","Invalid Manifest File of plugin ${plugin.name}")
            }




            InstalledPlugins.add(Plugin(manifest!!,plugin.absolutePath,this))
        }
    }

    companion object {
        private val InstalledPlugins = ArrayList<Plugin>()

        @JvmStatic
        fun getInstalledPlugins(){
            return synchronized(InstalledPlugins){ InstalledPlugins }
        }

        @JvmStatic
        fun Context.getPluginRoot(): File {
            return File(filesDir, "plugins")
        }

        @JvmStatic
        fun isPluginActive(context: Context, packageName: String,default:Boolean): Boolean {
            return true
            val sharedPreferences = context.applicationContext.getSharedPreferences("PluginPrefs", Context.MODE_PRIVATE)
            return sharedPreferences.getBoolean(packageName, default)
        }

        @JvmStatic
        fun setPluginActive(context: Context, packageName: String,active: Boolean){
            val sharedPreferences = context.applicationContext.getSharedPreferences("PluginPrefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean(packageName,active).apply()
        }
    }


}