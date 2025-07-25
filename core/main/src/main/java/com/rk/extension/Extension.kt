package com.rk.extension

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.errorDialog
import com.rk.libcommons.isMainThread
import com.rk.settings.Preference
import com.rk.settings.Settings
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
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
) {
    private var dexClassLoader: PathClassLoader? = null

    var isLoaded = false
        private set

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

    fun load(): ExtensionAPI? {
        apkFile.setReadOnly()
        dexClassLoader = PathClassLoader(
            apkFile.absolutePath,
            application.classLoader
        )

        if (isLoaded){
            throw RuntimeException("Extension $this is already loaded")
        }

        if (isMainThread()) {
            throw RuntimeException("Tried to execute extension on main thread")
        }

        val mainClassInstance = dexClassLoader!!.loadClass(mainClass)

        if (ExtensionAPI::class.java.isAssignableFrom(mainClassInstance)) {
            val instance = mainClassInstance.getDeclaredConstructor().newInstance() as? ExtensionAPI
                ?: throw RuntimeException("main class could not be cast to ExtensionAPI")

            instance.onPluginLoaded(this)
            return instance
        } else {
            errorDialog("mainClass of plugin $name does not override ExtensionAPI")
        }
        return null
    }
}
