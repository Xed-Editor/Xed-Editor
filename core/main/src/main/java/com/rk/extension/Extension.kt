package com.rk.extension

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.rk.libcommons.errorDialog
import com.rk.settings.Preference
import com.rk.settings.Settings
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class Extension(
    val name: String,
    val packageName: String,
    val mainClass: String,
    val version: String,
    val versionCode: Long,
    val apkFile: File,
    val application: Application
) : DexClassLoader(
    apkFile.absolutePath,
    application.codeCacheDir.absolutePath,
    null,
    application.classLoader
) {
    private var _isLoaded = false
    val isLoaded get() = _isLoaded

    override fun hashCode(): Int {
        return apkFile.absolutePath.hashCode()
    }

    override fun toString(): String {
        return packageName
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Extension) return false
        return packageName == other.packageName
    }

    fun load() {
        if (isLoaded){
            throw RuntimeException("Extension $this is already loaded")
        }
        if (Thread.currentThread().name == "main") {
            throw RuntimeException("Tried to execute extension on main thread")
        }

        val mainClassInstance = loadClass(mainClass)

        if (ExtensionAPI::class.java.isAssignableFrom(mainClassInstance)) {
            val instance = mainClassInstance.getDeclaredConstructor().newInstance() as? ExtensionAPI
                ?: throw RuntimeException("main class could not be cast to ExtensionAPI")

            ExtensionManager.extensions[this] = instance
            instance.onPluginLoaded(this)
        } else {
            throw RuntimeException("mainClass of plugin $name does not override ExtensionAPI")
        }
    }

    companion object {
        suspend fun loadExtensions(application: Application, scope: CoroutineScope) {
            ExtensionManager.indexPlugins(application)
            val pm = application.packageManager
            val xedVersionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(application.packageName, 0))
            ExtensionManager.extensions.keys.forEach { extension ->
                val info = pm.getPackageArchiveInfo(extension.apkFile.absolutePath, PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES)!!
                info.applicationInfo!!.sourceDir = extension.apkFile.absolutePath
                info.applicationInfo!!.publicSourceDir = extension.apkFile.absolutePath
                val appInfo = info.applicationInfo!!
                val metadata = appInfo.metaData

                val minSdkVersion = metadata.getInt("minXedVersionCode",-1)
                val targetSdkVersion = metadata.getInt("targetXedVersionCode",-1)

                if (minSdkVersion != -1 && targetSdkVersion != -1 && minSdkVersion <= xedVersionCode && targetSdkVersion <= xedVersionCode){
                    if (Preference.getBoolean("ext_${extension.packageName}", false) && ExtensionManager.extensions[extension] == null) {
                        scope.launch(Dispatchers.IO) {
                            extension.load()
                        }
                    }
                }else{
                    errorDialog("Plugin ${extension.packageName} failed to load due to incompatible sdk versions \nxedVersionCode = $xedVersionCode\nminSdkVersion = $minSdkVersion\ntargetSdkVersion = $targetSdkVersion")
                }


            }
        }
    }
}
