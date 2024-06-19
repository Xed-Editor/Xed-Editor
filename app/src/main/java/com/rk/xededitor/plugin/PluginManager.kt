package com.rk.xededitor.plugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
        fun activatePlugin(ctx: Context?, app: ApplicationInfo, active: Boolean) {
            val jsonString = rkUtils.getSetting(ctx, "activePlugins", "{}")
            try {
                val jsonObject = JSONObject(jsonString)
                jsonObject.put(app.packageName, active)
                val updatedJsonString = jsonObject.toString()
                rkUtils.setSetting(ctx, "activePlugins", updatedJsonString)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun isPluginActive(ctx: Context?, app: ApplicationInfo): Boolean {
            val jsonString = rkUtils.getSetting(ctx, "activePlugins", "{}")
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

        @JvmStatic
        fun extractDexFiles(ctx: Context?,packageName: String,outputDir: File): List<File> {
            val apkPath = getApkPath(ctx,packageName)
            val dexFiles = mutableListOf<File>()
            ZipFile(apkPath).use { zipFile ->
                zipFile.entries().asSequence().forEach { entry ->
                    if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                        val dexFile = File(outputDir, entry.name)
                        dexFile.outputStream().use { output ->
                            zipFile.getInputStream(entry).use { input ->
                                input.copyTo(output)
                            }
                        }
                        dexFiles.add(dexFile)
                    }
                }
            }
            return dexFiles
        }

        @JvmStatic
        fun loadAndInvokeMethod(ctx: Context, dexFile: File, className: String, methodName: String, methodArgs: Array<Any>) {
            val optimizedDir = File(ctx.cacheDir, "dex_optimized")
            if (!optimizedDir.exists()) optimizedDir.mkdirs()

            val dexClassLoader = DexClassLoader(dexFile.absolutePath, optimizedDir.absolutePath, null, ctx.classLoader)

            try {
                val loadedClass = dexClassLoader.loadClass(className)
                val method = loadedClass.getMethod(methodName, *methodArgs.map { it::class.java }.toTypedArray())
                val instance = loadedClass.getDeclaredConstructor().newInstance()
                val result = method.invoke(instance, *methodArgs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        @JvmStatic
        fun executeDexFromInstalledApk(ctx:Context,packageName: String, className: String, methodName: String, methodArgs: Array<Any>) {
            val dexOutputDir = File(ctx.filesDir, "extracted_dex")
            if (!dexOutputDir.exists()) dexOutputDir.mkdirs()
            val dexFiles = extractDexFiles(ctx,packageName,dexOutputDir)
            dexFiles.forEach { dexFile ->
                loadAndInvokeMethod(ctx,dexFile, className, methodName, methodArgs)
            }
        }
    }
}
