package com.rk.xededitor

import android.app.Application
import android.content.pm.PackageManager
import com.rk.xededitor.CrashHandler.CrashHandler
import com.rk.xededitor.Settings.SettingsData
import java.io.File


class App : Application() {

  override fun onCreate() {
    super.onCreate()
    CrashHandler.INSTANCE.init(this)
    rkUtils.initUi()
  }
}