package com.rk.extension

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.postIO
import com.rk.libcommons.toastCatching
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ExtensionManager : ExtensionAPI() {
    val isLoaded = mutableStateOf(false)
    val extensions = mutableStateMapOf<Extension, ExtensionAPI?>()

    init {
        if (isLoaded.value.not() && isPluginEnabled()) {
            postIO {
                indexPlugins(application!!)
                withContext(Dispatchers.Main) {
                    isLoaded.value = true
                }
            }
        }
    }

    suspend fun indexPlugins(context: Application): Map<Extension, ExtensionAPI?> =
        withContext(Dispatchers.IO) {
            context.pluginDir.listFiles()?.forEach { file ->
                if (file.exists() && file.isFile && file.canRead() && file.name.endsWith(".apk")) {
                    val pm = context.packageManager

                    // Get info from the APK file (not installed app)
                    val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES)!!

                    // Set APK sourceDir to make metadata accessible
                    info.applicationInfo!!.sourceDir = file.absolutePath
                    info.applicationInfo!!.publicSourceDir = file.absolutePath

                    val appInfo = info.applicationInfo!!

                    val metadata = appInfo.metaData

                    val versionCode = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P){
                        info.longVersionCode
                    }else{
                        info.versionCode.toLong()
                    }

                    val ext = Extension(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = appInfo.packageName,
                        mainClass = metadata.getString("mainClass")!!,
                        version = info.versionName!!,
                        versionCode = versionCode,
                        application = context,
                        apkFile = file
                    )

                    //add extension
                    if (extensions[ext] == null) {
                        extensions[ext] = null
                    }
                }
            }

            extensions
        }

    suspend fun deletePlugin(extension: Extension) = withContext(Dispatchers.IO) {
        extension.apkFile.delete()
        extensions.remove(extension)
        runCatching {
            Preference.removeKey("ext_" + extension.packageName)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun installPlugin(context: Application, apkFile: File) =
        withContext(Dispatchers.IO) {

            if (!isPluginEnabled()) return@withContext null

            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES)!!

            // Set APK sourceDir to make metadata accessible
            info.applicationInfo!!.sourceDir = apkFile.absolutePath
            info.applicationInfo!!.publicSourceDir = apkFile.absolutePath

            val appInfo = info.applicationInfo!!

            val destFile = context.pluginDir.child(appInfo.packageName+".apk")

            // Make sure the directory exists
            destFile.parentFile?.mkdirs()

            // Copy the APK to plugin directory
            apkFile.copyTo(destFile, overwrite = true)

            val metadata = appInfo.metaData

            val versionCode = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P){
                info.longVersionCode
            }else{
                info.versionCode.toLong()
            }

            val ext = Extension(
                name = pm.getApplicationLabel(appInfo).toString(),
                packageName = appInfo.packageName,
                mainClass = metadata.getString("mainClass")!!,
                version = info.versionName!!,
                versionCode = versionCode,
                application = context,
                apkFile = destFile
            )

            //add extension
            if (extensions[ext] == null) {
                extensions[ext] = null
            }

            return@withContext null
        }

    private fun isPluginEnabled():Boolean{
        return InbuiltFeatures.extensions.state.value
    }

    override fun onPluginLoaded(extension: Extension) {
        if (isPluginEnabled().not()){
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onPluginLoaded(ext) }
            }

        }
    }

    override fun onMainActivityCreated() {
        if (isPluginEnabled().not()){
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityCreated() }
            }

        }
    }

    override fun onMainActivityPaused() {
        if (isPluginEnabled().not()){
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityPaused() }
            }

        }
    }

    override fun onMainActivityResumed() {
        if (isPluginEnabled().not()){
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityResumed() }
            }

        }
    }

    override fun onMainActivityDestroyed() {
        if (isPluginEnabled().not()){
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityDestroyed() }
            }

        }
    }


    override fun onLowMemory() {
        if (isPluginEnabled().not()){
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onLowMemory() }
            }

        }

    }
}