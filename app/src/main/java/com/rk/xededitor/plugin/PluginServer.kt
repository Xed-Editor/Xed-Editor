package com.rk.xededitor.plugin

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import com.rk.xedplugin.API
import dalvik.system.DexClassLoader
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantLock


class PluginServer(private val ctx: Application) : Thread() {
  private val pluginKey = "xedpluginAPI"
  private val entryPointKey = "EntryPoint"
  private var entryPointClassName = ""
  private val tag = "PluginServer"
  
  companion object {
    @JvmStatic
    private val apiVersion = 1
    
    @JvmStatic
    private val minApiVersion = 1
    
    @JvmStatic
    var loadedPlugins: ArrayList<PluginInstance>? = null
    
    @JvmStatic
    var arrayOfPluginNames = ArrayList<String>()
    
    @JvmStatic
    var arrayOfPluginIcons = ArrayList<Drawable>()
    
    @JvmStatic
    var arrayOfPluginPackageNames = ArrayList<String>()
    
    @JvmStatic
    var pluginsinfo = mutableListOf<ApplicationInfo>()
    
    @JvmStatic
    var isRunning = false
    
    
  }
  
  private val lock = ReentrantLock()
  
  init {
    lock.lock()
    loadedPlugins = ArrayList()
    lock.unlock()
  }
  
  private fun info(info: String) {
    Log.i(tag, info)
  }
  
  private fun err(error: String) {
    Log.e(tag, error)
  }
  
  
  override fun run() {
    if (isRunning) {
      err("plugin server is already running trying to clean start a new thread")
      lock.lock()
      loadedPlugins = ArrayList()
      lock.unlock()
      arrayOfPluginNames = ArrayList<String>()
      arrayOfPluginIcons = ArrayList<Drawable>()
      arrayOfPluginPackageNames = ArrayList<String>()
      pluginsinfo = mutableListOf<ApplicationInfo>()
      
    }
    
    if (!SettingsData.getBoolean(ctx, "enablePlugins", false)) {
      info("plugins are disabled server won't start")
      return
    }
    isRunning = true
    info("plugin server started")
    
    info("apiVersion : $apiVersion")
    info("minimumApiVersion : $minApiVersion")
    
    
    var jsonObject: JSONObject? = null
    try {
      jsonObject = JSONObject(SettingsData.getSetting(ctx, "pluginHash", "{}"))
    } catch (e: JSONException) {
      e.printStackTrace()
    }
    
    
    val pm = ctx.packageManager
    val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
    } else {
      pm.getInstalledApplications(0)
    }
    
    
    for (app in apps) {
      
      val metaData = pm.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
      if (metaData != null && metaData.containsKey(pluginKey) && metaData.containsKey(entryPointKey)) {
        entryPointClassName = metaData.getString(entryPointKey, "")
        
        if (entryPointClassName.isNotEmpty()) {
          
          info("detected plugin : " + app.packageName)
          val pluginApiVersion = metaData.getInt(pluginKey, 0)
          if (pluginApiVersion in minApiVersion..apiVersion) {
            pluginsinfo.add(app)
            arrayOfPluginIcons.add(app.loadIcon(pm))
            arrayOfPluginNames.add(pm.getApplicationLabel(app).toString())
            arrayOfPluginPackageNames.add(app.packageName)
          } else {
            err("failed to load plugin : ${app.packageName} \n apiVersion : $pluginApiVersion")
          }
        }
        
      }
    }
    
    if (pluginsinfo.isNotEmpty()) {
      //plugins are installed
      for (plugininfo in pluginsinfo) {
        val apkpath = PluginManager.getApkPath(ctx, plugininfo.packageName)
        val md5sum = rkUtils.calculateMD5(apkpath?.let { File(it) })
        val hash = jsonObject?.optString(plugininfo.packageName)
        
        if (hash.isNullOrEmpty()) {
          jsonObject?.put(plugininfo.packageName, md5sum)
        } else if (hash != md5sum) {
          info("updated plugin " + plugininfo.packageName)
          info("deleting code_cache dir")
          ctx.codeCacheDir.delete()
        }
        
        
        
        if (PluginManager.isPluginActive(ctx, plugininfo.packageName)) {
          val classLoader = DexClassLoader(
            apkpath,
            ctx.codeCacheDir.absolutePath,
            null,
            this.javaClass.getClassLoader()
          )
          try {
            val mClass = classLoader.loadClass(entryPointClassName)
            val instance = mClass.getDeclaredConstructor().newInstance()
            val classMethods = mClass.declaredMethods
            val methodNames = ArrayList<String>()
            
            for (method in classMethods) {
              methodNames.add(method.name)
            }
            
            val targetInterface: Class<*> = API::class.java
            val interfaceMethods: Array<Method> = targetInterface.declaredMethods
            
            
            var shouldContinue = true
            
            for (method in interfaceMethods) {
              if (!methodNames.contains(method.name)) {
                shouldContinue = false
                err("plugin does not implement plugin API properly : " + plugininfo.packageName)
              }
            }
            
            if (shouldContinue) {
              info("starting plugin : " + plugininfo.packageName)
              val pluginInstance = PluginInstance(mClass, instance)
              
              pluginInstance.onLoad(ctx)
              
              lock.lock()
              loadedPlugins!!.add(pluginInstance)
              lock.unlock()
              
            } else {
              PluginManager.activatePlugin(ctx, plugininfo.packageName, false)
            }
          } catch (e: Exception) {
            e.printStackTrace()
          }
          
        } else {
          info("Ignoring disabled plugin : " + plugininfo.packageName)
        }
        
      }
    } else {
      info("no plugins are installed")
    }
    SettingsData.setSetting(ctx, "pluginHash", jsonObject.toString())
  }
}