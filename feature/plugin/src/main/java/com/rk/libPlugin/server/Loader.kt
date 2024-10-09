package com.rk.libPlugin.server

import android.app.Application
import com.rk.libPlugin.server.PluginUtils.getInstalledPlugins
import com.rk.libPlugin.server.PluginUtils.indexPlugins
import com.rk.libPlugin.server.PluginUtils.isPluginActive
import com.rk.libPlugin.server.api.API
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class Loader(val app: Application) {
    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        GlobalScope.launch(Dispatchers.Default) {
            API.application = app
            app.indexPlugins()
            getInstalledPlugins().forEach { plugin: Plugin ->
                try {
                    if (isPluginActive(app, plugin.info.packageName, false)) {
                        plugin.start()
                    }
                }catch (e:Exception){
                    PluginError.showError(e)
                }
                
            }
        }
    }
}