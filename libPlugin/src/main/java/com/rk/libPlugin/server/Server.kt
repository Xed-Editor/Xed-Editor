package com.rk.libPlugin.server

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.google.gson.Gson
import com.rk.libPlugin.server.api.API
import java.io.File

//private const val TAG="PluginServer"
class Server(val app: Application) : Thread() {

    override fun run() {
        API.application = app
        app.indexPlugins()
        InstalledPlugins.forEach { plugin: Plugin ->
            if (isPluginActive(app,plugin.info.packageName,false)){
                plugin.start()
            }
        }
    }

    private fun Application.indexPlugins(){
        val root = getPluginRoot()
        val pluginsFiles = root.listFiles()
        if (root.exists().not()and(pluginsFiles.isNullOrEmpty())){return}

        for (plugin in pluginsFiles!!) {
            val manifestFile = File(plugin, "manifest.json")

            var manifest: Manifest? = null
            try {
                val json = manifestFile.readText()
                val gson = Gson()
                manifest = gson.fromJson(json, Manifest::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(app,"PluginError ${e.message}",Toast.LENGTH_LONG).show()
            }

            InstalledPlugins.add(Plugin(manifest!!,plugin.absolutePath,this))
        }
    }

    companion object {
        private val InstalledPlugins = ArrayList<Plugin>()

        @JvmStatic
        fun getInstalledPlugins() : List<Plugin>{
            return synchronized(InstalledPlugins){ InstalledPlugins }
        }

        @JvmStatic
        fun Context.getPluginRoot(): File {
            return File(filesDir.parentFile, "plugins")
        }

        @JvmStatic
        fun isPluginActive(context: Context, packageName: String,default:Boolean): Boolean {
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