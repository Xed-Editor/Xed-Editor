package com.rk.extension

import android.app.Application
import com.rk.settings.PreferencesData
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File



class ExtensionLoader(val application: Application, val extension: Extension) : DexClassLoader(
    extension.dexFiles.joinToString(separator = File.pathSeparator) { it.absolutePath },
    application.codeCacheDir.absolutePath,
    null,
    application.classLoader
) {

    fun execute() {
        if (Thread.currentThread().name == "main") {
            throw RuntimeException("Tried to execute extension on main thread")
        }

        val mainClass = loadClass(extension.mainClass)

        if (ExtensionAPI::class.java.isAssignableFrom(mainClass)) {
            val mainInstance = mainClass.getDeclaredConstructor().newInstance() as? ExtensionAPI
                ?: throw RuntimeException("main class could not be cast to ExtensionAPI")

                ExtensionManager.extensions[extension] = mainInstance
                mainInstance.onPluginLoaded()
        } else {
            throw RuntimeException("mainClass of plugin ${extension.name} does not override ExtensionAPI")
        }

    }


    companion object {
        suspend fun loadExtensions(application: Application, scope: CoroutineScope) {

            ExtensionManager.loadExistingPlugins(application)
            ExtensionManager.extensions.keys.forEach {
                if (PreferencesData.getBoolean("ext_${it.packageName}", false)) {
                    scope.launch(Dispatchers.IO) {
                        val loader = ExtensionLoader(application, it)
                        loader.execute()
                    }
                }
            }
        }
    }

}