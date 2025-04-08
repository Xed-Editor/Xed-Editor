package com.rk.extension

import android.app.Application
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

    override fun hashCode(): Int {
        return apkFile.absolutePath.hashCode()
    }

    override fun toString(): String {
        return "Extension : $packageName"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Extension) return false
        return packageName == other.packageName
    }

    fun execute() {
        if (Thread.currentThread().name == "main") {
            throw RuntimeException("Tried to execute extension on main thread")
        }

        val mainClassInstance = loadClass(mainClass)

        if (ExtensionAPI::class.java.isAssignableFrom(mainClassInstance)) {
            val instance = mainClassInstance.getDeclaredConstructor().newInstance() as? ExtensionAPI
                ?: throw RuntimeException("main class could not be cast to ExtensionAPI")

            ExtensionManager.extensions[this] = instance
            instance.onPluginLoaded()
        } else {
            throw RuntimeException("mainClass of plugin $name does not override ExtensionAPI")
        }
    }

    companion object {
        suspend fun executeExtensions(application: Application, scope: CoroutineScope) {
            ExtensionManager.loadExistingPlugins(application)
            ExtensionManager.extensions.keys.forEach { extension ->
                if (Preference.getBoolean("ext_${extension.packageName}", false) && ExtensionManager.extensions[extension] == null) {
                    delay(Random(492).nextLong(10,300))
                    scope.launch(Dispatchers.IO) {
                        extension.execute()
                    }
                }
            }
        }
    }
}
