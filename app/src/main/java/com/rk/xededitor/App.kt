package com.rk.xededitor

import android.app.Application
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.plugin.PluginManager
import java.io.File

class App : Application() {
  override fun onCreate() {
    super.onCreate()
    CrashHandler.INSTANCE.init(this)
    val start = System.currentTimeMillis()
    Async.run {
      val apkpath = PluginManager.getApkPath(this@App, packageName)
      val md5 = SettingsData.getSetting(this@App, "selfmd5", "")
      if (md5.isEmpty()) {
        SettingsData.setSetting(
          this@App,
          "selfmd5",
          rkUtils.calculateMD5(apkpath?.let { File(it) })
        )
      } else if (!md5.equals(rkUtils.calculateMD5(apkpath?.let { File(it) }))) {
        codeCacheDir.delete()
      }
      println(System.currentTimeMillis()-start)
      
    }
    
  }
}