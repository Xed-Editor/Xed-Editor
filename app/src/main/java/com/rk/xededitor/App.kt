package com.rk.xededitor

import android.app.Application
import android.content.Context
import androidx.annotation.Keep
import com.rk.libcommons.SetupEditor
import com.rk.libcommons.application
import com.rk.settings.PreferencesData
import com.rk.xededitor.CrashHandler.CrashHandler
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
        var app: Application? = null
        
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
        PreferencesData.initPref(this)
        // create crash handler
        CrashHandler.INSTANCE.init(this)
        
        GlobalScope.launch(Dispatchers.IO) {
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
