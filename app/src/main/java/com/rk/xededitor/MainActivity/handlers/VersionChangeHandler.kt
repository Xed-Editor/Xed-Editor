package com.rk.xededitor.MainActivity.handlers

import android.os.Build
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object VersionChangeHandler {
    fun handle(app: App) {
        val previousVersionCode = PreferencesData.getString(PreferencesKeys.VERSION_CODE, "0")
        
        val pkgInfo = try {
            app.packageManager.getPackageInfo(app.packageName, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            return // Exit if package info cannot be retrieved
        }
        
        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        }
        
        try {
            if (previousVersionCode.isEmpty() || previousVersionCode.toLong() == 0L) {
                // User may be updating from a very old version, clear data
                app.filesDir.parentFile?.deleteRecursively()
                PreferencesData.setString(PreferencesKeys.VERSION_CODE, currentVersionCode.toString())
                return
            } else if (previousVersionCode.toLong() != currentVersionCode) {
                // User updated the app
                PreferencesData.setString(PreferencesKeys.VERSION_CODE, currentVersionCode.toString())
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            // Handle potential conversion errors
            PreferencesData.setString(PreferencesKeys.VERSION_CODE, currentVersionCode.toString())
        }
    }
}
