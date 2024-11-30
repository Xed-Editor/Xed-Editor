package com.rk.xededitor

import android.app.Application
import android.content.Context
import com.rk.libcommons.application
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.MainActivity.handlers.VersionChangeHandler
import com.rk.xededitor.ui.screens.settings.terminal.updateProotArgs
import com.rk.xededitor.update.UpdateManager
import com.rk.libcommons.SetupEditor
import com.rk.resources.Res
import com.rk.settings.PreferencesData
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
        Res.context = this
        
        super.onCreate()
        // create crash handler
        CrashHandler.INSTANCE.init(this)
        PreferencesData.initPref(this)
        GlobalScope.launch(Dispatchers.IO) {
            //wait for version change handler
            VersionChangeHandler.handle(this@App)
            launch(Dispatchers.IO) {
                delay(1000)
                updateProotArgs(this@App)
            }
            launch(Dispatchers.IO) {
                SetupEditor.init(this@App)
            }
            delay(6000)
            //check for updates
            
            try {
                UpdateManager.fetch("dev")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        }
        
    }
    
    override fun onTerminate() {
        getTempDir().deleteRecursively()
        super.onTerminate()
    }
}
