package com.rk.xededitor.plugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import dalvik.system.DexClassLoader
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class PluginManager {
    companion object {
        @JvmStatic
        fun activatePlugin(ctx: Context?, packageName:String, active: Boolean) {
            val jsonString = SettingsData.getSetting(ctx, "activePlugins", "{}")
            try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.put(packageName, active)
                val updatedJsonString = jsonObject.toString()
                SettingsData.setSetting(ctx, "activePlugins", updatedJsonString)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun isPluginActive(ctx: Context?, app: ApplicationInfo): Boolean {
            val jsonString = SettingsData.getSetting(ctx, "activePlugins", "{}")
            var toReturn = false
            try {
                val jsonObject = JSONObject(jsonString)
                toReturn = if (jsonObject.has(app.packageName)) {
                    jsonObject.getBoolean(app.packageName)
                } else {
                    return false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return toReturn
        }

        @JvmStatic
        fun getApkPath(ctx: Context?, packageName: String): String? {
            val packageManager = ctx?.packageManager
            return try {
                val packageInfo = packageManager?.getPackageInfo(packageName, 0)
                packageInfo?.applicationInfo?.sourceDir
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                null
            }
        }
    }
}
