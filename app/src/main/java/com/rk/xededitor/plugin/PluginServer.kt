package com.rk.xededitor.plugin

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dalvik.system.DexClassLoader
import java.lang.reflect.Method
import kotlin.collections.ArrayList


class PluginServer(private val ctx: Application) : Thread() {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val pluginKey = "xedpluginAPI"
    private val EntryPointKey = "EntryPoint"
    private var entryPointClassName = ""

    companion object{
        @JvmStatic
        var loadedPlugins:ArrayList<PluginInstance>? = null
    }

    private fun runOnUiThread(runnable: Runnable) {
        handler.post(runnable)
    }

    init {
        loadedPlugins = ArrayList()
    }
    override fun run() {
        runOnUiThread {
            println("plugin server started")


            val pm = ctx.packageManager
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                pm.getInstalledApplications(0)
            }

            val pluginsinfo = mutableListOf<ApplicationInfo>()
            for (app in apps) {
                val metaData =
                    pm.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
                if (metaData != null && metaData.containsKey(pluginKey) && metaData.containsKey(
                        EntryPointKey
                    )
                ) {
                    entryPointClassName = metaData.getString(EntryPointKey, "")
                    if (entryPointClassName.isNotEmpty()) {
                        pluginsinfo.add(app)
                        println("plugin : " + app.packageName)
                    }

                }
            }

            if (pluginsinfo.isNotEmpty()) {
                //plugins are installed
                for (plugininfo in pluginsinfo) {
                    //if (!PluginManager.isPluginActive(ctx, plugin)) {

                    val apkpath = PluginManager.getApkPath(ctx, plugininfo.packageName)

                    val classLoader = DexClassLoader(
                        apkpath, null, null, this.javaClass.getClassLoader()
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
                                Log.e(
                                    "PluginServer",
                                    "plugin dosen't implement plugin api properly : " + plugininfo.packageName
                                )
                            }
                        }

                        if (shouldContinue) {
                            println("starting plugin : " + plugininfo.packageName)
                            val pluginInstance = PluginInstance(plugininfo,mClass,instance)
                            pluginInstance.onLoad(ctx)
                            loadedPlugins!!.add(pluginInstance)

                        }else{
                            PluginManager.activatePlugin(ctx,plugininfo.packageName,false)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    //  } else {
                    //    println("Ignoring disabled plugin : " + plugin.packageName)
                    //}

                }
            } else {
                println("no plugins are installed")
            }

            ctx.codeCacheDir.delete()
            println("plugin server will now stop")
        }
    }
}