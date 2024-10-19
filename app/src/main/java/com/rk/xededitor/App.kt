package com.rk.xededitor

import android.app.Application
import android.content.Context
import com.rk.libcommons.application
import com.rk.plugin.server.Loader
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.MainActivity.handlers.VersionChangeHandler
import com.rk.xededitor.ui.screens.settings.terminal.updateProotArgs
import com.rk.xededitor.update.UpdateManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {
    
    companion object {
        @Deprecated("use libcommons application instead")
        lateinit var app: Application
        
        inline fun Context.getTempDir(): File {
            val tmp = File(filesDir.parentFile, "tmp")
            if (!tmp.exists()) {
                tmp.mkdir()
            }
            return tmp
        }
    }
    
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        app = this
        application = this
        super.onCreate()
        // create crash handler
        CrashHandler.INSTANCE.init(this)
        
        GlobalScope.launch(Dispatchers.IO) {
            //wait for version change handler
            VersionChangeHandler.handle(this@App)
            launch(Dispatchers.IO) {
                delay(1000)
                updateProotArgs(this@App)
            }
            launch(Dispatchers.IO) {
                delay(5000)
                val pluginLoader = Loader(this@App)
                pluginLoader.start()
            }
            launch(Dispatchers.IO){
                SetupEditor.init(this@App)
            }
            delay(6000)
            //check for updates
            UpdateManager.fetch("dev")
        }
        
    }
}
