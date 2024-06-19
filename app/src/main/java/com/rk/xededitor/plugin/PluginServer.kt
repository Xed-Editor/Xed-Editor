package com.rk.xededitor.plugin

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.rk.xededitor.MainActivity.MainActivity

class PluginServer(activity: MainActivity) : Thread(),Plugin {
    private val handler = Handler(Looper.getMainLooper())
    private val ctx = activity
    val pluginKey = "xedpluginAPI"
    override fun run() {


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
        if(!plugins.isEmpty()){
            //plugins are installed


            for (plugin in plugins){
               if (PluginManager.isPluginActive(ctx,plugin)){

                    PluginManager.executeDexFromInstalledApk(ctx,plugin.packageName,"PluginMain","Main",
                        arrayOf(this)
                    )
               }


            }
            



        }







    }

    override fun getActivity(): Activity {
        return ctx;
    }

    override fun runOnUiThread(runnable: Runnable?) {
        if (runnable != null) {
            handler.post(runnable)
        }
    }

}