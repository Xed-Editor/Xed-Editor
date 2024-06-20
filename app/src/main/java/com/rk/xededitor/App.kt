package com.rk.xededitor

import android.app.Application
import com.rk.xededitor.plugin.PluginServer

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        println("---------------------------------------------------------------------------------")
        println("application started")

        println("starting plugin server")
        val pluginServer = PluginServer(this)
        pluginServer.start()

    }
}