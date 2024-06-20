package com.rk.xededitor.plugin

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.rkUtils

class PluginServer(val ctx: Context) : Thread(){
    private val handler:Handler;
    private val pluginKey = "xedpluginAPI"

    init {
        handler = Handler(Looper.getMainLooper())
    }

    private fun runOnUiThread(runnable: Runnable){
        handler.post(runnable)
    }

    override fun run() {

        println("plugin server started")

        val pm = ctx.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }

        val plugins = mutableListOf<ApplicationInfo>()
        for (app in apps){
                val metaData = pm.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
                if (metaData != null && metaData.containsKey(pluginKey)) {
                    plugins.add(app)
                }
        }
        if(plugins.isNotEmpty()){
            //plugins are installed
            for (plugin in plugins){
               if (PluginManager.isPluginActive(ctx,plugin)){
                   println("starting plugin : "+plugin.packageName)
                   val apkpath = PluginManager.getApkPath(ctx,plugin.packageName)
                   println(apkpath)

               }else{
                   println("disabled plugin : "+plugin.packageName)
               }


            }
        }else{
            println("no plugins are installed")
        }





        println("plugin server will now stop")


    }


}