package com.rk.extension

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.pm.PackageInfoCompat
import com.rk.App
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.dialog
import com.rk.libcommons.errorDialog
import com.rk.libcommons.postIO
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ExtensionManager : ExtensionAPI() {
    private val mutex = Mutex()
    val isIndexing = mutableStateOf(false)
    val indexedExtension = mutableStateListOf<Extension>()
    private val loadedExtensions = mutableStateMapOf<Extension, ExtensionAPI>()
    private var isLoaded = false

    suspend fun indexPlugins(application: Application) = withContext(Dispatchers.IO){
        mutex.lock()
        isIndexing.value = true

            runCatching {
                indexedExtension.clear()
                val pm = application.packageManager
                val xedVersionCode =
                    PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(application.packageName, 0))

                application.pluginDir.listFiles()?.forEach { file ->
                    if (file.exists() && file.isFile && file.canRead() && file.name.endsWith(".apk")) {
                        val info = pm.getPackageArchiveInfo(
                            file.absolutePath,
                            PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
                        )!!
                        info.applicationInfo!!.sourceDir = file.absolutePath
                        info.applicationInfo!!.publicSourceDir = file.absolutePath

                        val appInfo = info.applicationInfo!!

                        val metadata = appInfo.metaData

                        val versionCode = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                            info.longVersionCode
                        } else {
                            info.versionCode.toLong()
                        }

                        val minSdkVersion = metadata.getInt("minXedVersionCode", -1)
                        val targetSdkVersion = metadata.getInt("targetXedVersionCode", -1)

                        val ext = Extension(
                            name = pm.getApplicationLabel(appInfo).toString(),
                            packageName = appInfo.packageName,
                            mainClass = metadata.getString("mainClass")!!,
                            version = info.versionName!!,
                            versionCode = versionCode,
                            application = application,
                            apkFile = file
                        )

                        if (!(minSdkVersion <= xedVersionCode && targetSdkVersion <= xedVersionCode)) {
                            Preference.setBoolean("ext_${ext.packageName}", false)
                        }

                        indexedExtension.add(ext)
                    }
                }
            }.onFailure {
                errorDialog(it)
            }


        isIndexing.value = false
        mutex.unlock()
    }

    suspend fun loadPlugins(application: Application) = with(application) {
        mutex.lock()
        application.pluginDir.child("oat").mkdirs()
        indexedExtension.forEach{ ext ->
            withContext(Dispatchers.IO){
                if (Preference.getBoolean("ext_${ext.packageName}", false)) {
                    val instance = ext.load()
                    if (instance != null){
                        loadedExtensions[ext] = instance
                    }else{
                        errorDialog("Plugin ${ext.packageName} failed to load")
                    }
                }
            }
        }
        isLoaded = true
        mutex.unlock()
    }

    private fun deleteFilesWithPackageName(file: File,pkg: String){
        file.let {
            if (it.isDirectory){
                it.listFiles()?.forEach {
                    deleteFilesWithPackageName(it,pkg)
                }
            }else{
                if (it.name.startsWith(pkg)){
                    it.delete()
                }
            }
        }
    }

    suspend fun deletePlugin(extension: Extension) = withContext(Dispatchers.IO) {
        indexedExtension.remove(extension)
        deleteFilesWithPackageName(application!!.pluginDir.child("oat"),extension.packageName)
        extension.apkFile.delete()
        runCatching {
            Preference.removeKey("ext_" + extension.packageName)
        }
    }

    override fun onPluginLoaded(extension: Extension) {
        throw IllegalAccessException("This function was not supposed to be called")
    }

    @Deprecated("Use onPluginLoaded function instead")
    override fun onMainActivityCreated() {
        DefaultScope.launch(Dispatchers.Default){
            mutex.withLock{
                loadedExtensions.values.forEach{
                    DefaultScope.launch {
                        it.onMainActivityCreated()
                    }
                }
            }
        }
    }

    override fun onMainActivityPaused() {
        DefaultScope.launch(Dispatchers.Default){
            mutex.withLock{
                loadedExtensions.values.forEach{ DefaultScope.launch  { it.onMainActivityPaused() } }
            }
        }

    }

    override fun onMainActivityResumed() {
        DefaultScope.launch(Dispatchers.Default){
            mutex.withLock{
                loadedExtensions.values.forEach{ DefaultScope.launch { it.onMainActivityResumed() } }
            }
        }

    }

    override fun onMainActivityDestroyed() {
        DefaultScope.launch(Dispatchers.Default){
            mutex.withLock{
                loadedExtensions.values.forEach{ DefaultScope.launch { it.onMainActivityDestroyed() } }
            }
        }

    }

    override fun onLowMemory() {
        DefaultScope.launch(Dispatchers.Default){
            mutex.withLock{
                loadedExtensions.values.forEach{ DefaultScope.launch { it.onLowMemory() } }
            }
        }

    }


    @OptIn(DelicateCoroutinesApi::class)
    suspend fun installPlugin(fileObject: FileObject,application: Application) =
        withContext(Dispatchers.IO) {
            val apkFile = File(App.getTempDir(), fileObject.getName()).createFileIfNot()
            runCatching {
                fileObject.getInputStream().use { inputStream ->
                    FileOutputStream(apkFile).use {
                        inputStream.copyTo(it)
                    }
                }

                val pm = application.packageManager
                val info = pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
                )!!

                // Set APK sourceDir to make metadata accessible
                info.applicationInfo!!.sourceDir = apkFile.absolutePath
                info.applicationInfo!!.publicSourceDir = apkFile.absolutePath

                val appInfo = info.applicationInfo!!

                val destFile = application.pluginDir.child(appInfo.packageName + ".apk")

                // Make sure the directory exists
                destFile.parentFile?.mkdirs()

                val metadata = appInfo.metaData

                val minSdkVersion = metadata.getInt("minXedVersionCode", -1)
                val targetSdkVersion = metadata.getInt("targetXedVersionCode", -1)
                val xedVersionCode = PackageInfoCompat.getLongVersionCode(
                    application.packageManager.getPackageInfo(
                        application.packageName,
                        0
                    )
                )


                val versionCode = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }

                deleteFilesWithPackageName(application.pluginDir.child("oat"),appInfo.packageName)

                val ext = Extension(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    mainClass = metadata.getString("mainClass")!!,
                    version = info.versionName!!,
                    versionCode = versionCode,
                    application = application,
                    apkFile = destFile
                )

                if (minSdkVersion != -1 && targetSdkVersion != -1 && minSdkVersion <= xedVersionCode && targetSdkVersion <= xedVersionCode) {

                    // Copy the APK to plugin directory
                    apkFile.copyTo(destFile, overwrite = true)

                    //add extension
                    indexedExtension.add(ext)

                    toast(strings.success)

                    if (apkFile.exists()) {
                        apkFile.delete()
                    }
                } else {
                    val reason: String =
                        if (minSdkVersion > xedVersionCode && minSdkVersion != -1 && targetSdkVersion != -1) {
                            "Xed-Editor is outdated minimum version code required is $minSdkVersion while current version code is $xedVersionCode"
                        } else if (targetSdkVersion < xedVersionCode && minSdkVersion != -1 && targetSdkVersion != -1) {
                            "Plugin ${ext.name} was made for an older version of Xed-Editor, ask the plugin developer to update the plugin"
                        } else if (minSdkVersion == -1 || targetSdkVersion == -1) {
                            "Undefined minXedVersionCode or targetXedVersionCode"
                        } else {
                            "Unknown error while parsing Xed Version code info from plugin"
                        }

                    errorDialog("Installation of plugin ${ext.name} failed \nreason: \n$reason")
                }
            }.onFailure {
                errorDialog(it)
                if (apkFile.exists()) {
                    apkFile.delete()
                }
            }

            return@withContext null
        }
}