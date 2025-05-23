package com.rk.extension

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.pm.PackageInfoCompat
import com.rk.App
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.dialog
import com.rk.libcommons.errorDialog
import com.rk.libcommons.isFdroid
import com.rk.libcommons.postIO
import com.rk.libcommons.toast
import com.rk.libcommons.toastCatching
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

            if (isFdroid.not()) {
                return@withContext emptyMap<Extension, ExtensionAPI?>()
            }

            val pm = context.packageManager
            val xedVersionCode =
                PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))

            context.pluginDir.listFiles()?.forEach { file ->

                runCatching {
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
                            application = context,
                            apkFile = file
                        )

                        if (!(minSdkVersion <= xedVersionCode && targetSdkVersion <= xedVersionCode)) {
                            //disable plugin
                            Preference.setBoolean("ext_${ext.packageName}", false)
                        }

                        extensions[ext] = extensions[ext]

                    }
                }.onFailure { errorDialog(it) }
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
    suspend fun installPlugin(context: Activity, fileObject: FileObject) =
        withContext(Dispatchers.IO) {
            val apkFile = File(App.getTempDir(), fileObject.getName()).createFileIfNot()
            runCatching {
                if (!isPluginEnabled()) return@withContext null

                fileObject.getInputStream().use { inputStream ->
                    FileOutputStream(apkFile).use {
                        inputStream.copyTo(it)
                    }
                }

                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
                )!!

                // Set APK sourceDir to make metadata accessible
                info.applicationInfo!!.sourceDir = apkFile.absolutePath
                info.applicationInfo!!.publicSourceDir = apkFile.absolutePath

                val appInfo = info.applicationInfo!!

                val destFile = context.pluginDir.child(appInfo.packageName + ".apk")

                // Make sure the directory exists
                destFile.parentFile?.mkdirs()

                val metadata = appInfo.metaData

                val minSdkVersion = metadata.getInt("minXedVersionCode", -1)
                val targetSdkVersion = metadata.getInt("targetXedVersionCode", -1)
                val xedVersionCode = PackageInfoCompat.getLongVersionCode(
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    )
                )


                val versionCode = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }

                val ext = Extension(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    mainClass = metadata.getString("mainClass")!!,
                    version = info.versionName!!,
                    versionCode = versionCode,
                    application = application!!,
                    apkFile = destFile
                )

                if (minSdkVersion != -1 && targetSdkVersion != -1 && minSdkVersion <= xedVersionCode && targetSdkVersion <= xedVersionCode) {

                    // Copy the APK to plugin directory
                    apkFile.copyTo(destFile, overwrite = true)

                    //add extension
                    if (extensions[ext] == null) {
                        extensions[ext] = null
                    }

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

                    dialog(
                        context = context,
                        title = strings.failed.getString(),
                        msg = "Installation of plugin ${ext.name} failed \nreason: \n$reason",
                        onOk = {})
                }
            }.onFailure {
                errorDialog(it)
                if (apkFile.exists()) {
                    apkFile.delete()
                }
            }

            return@withContext null
        }

    private fun isPluginEnabled(): Boolean {
        return InbuiltFeatures.extensions.state.value
    }

    override fun onPluginLoaded(extension: Extension) {
        if (isPluginEnabled().not()) {
            return
        }

        extensions.forEach { (ext, instance) ->
            postIO {
                toastCatching { instance?.onPluginLoaded(ext) }
            }

        }
    }

    override fun onMainActivityCreated() {
        if (isPluginEnabled().not()) {
            return
        }

        extensions.forEach { (_, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityCreated() }
            }

        }
    }

    override fun onMainActivityPaused() {
        if (isPluginEnabled().not()) {
            return
        }

        extensions.forEach { (_, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityPaused() }
            }

        }
    }

    override fun onMainActivityResumed() {
        if (isPluginEnabled().not()) {
            return
        }

        extensions.forEach { (_, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityResumed() }
            }

        }
    }

    override fun onMainActivityDestroyed() {
        if (isPluginEnabled().not()) {
            return
        }

        extensions.forEach { (_, instance) ->
            postIO {
                toastCatching { instance?.onMainActivityDestroyed() }
            }

        }
    }


    override fun onLowMemory() {
        if (isPluginEnabled().not()) {
            return
        }

        extensions.forEach { (_, instance) ->
            postIO {
                toastCatching { instance?.onLowMemory() }
            }

        }

    }
}