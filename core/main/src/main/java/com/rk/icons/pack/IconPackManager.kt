package com.rk.icons.pack

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import com.rk.file.child
import com.rk.file.createDirIfNot
import com.rk.file.localDir
import com.rk.settings.Settings
import com.rk.utils.toast
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val currentIconPack = mutableStateOf<IconPack?>(null)
val iconPackDir = localDir().child("icon_pack").also { it.createDirIfNot() }

class IconPackManager(private val context: Application) {
    val iconPacks = mutableStateMapOf<IconPackId, IconPack>()

    suspend fun installIconPack(zipFile: File) =
        withContext(Dispatchers.IO) {
            // Extract to temp dir first
            val tempDir = File(context.cacheDir, "icon_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (!entry.isDirectory) {
                            val target = tempDir.resolve(entry.name)
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }

                installIconPackFromDir(tempDir)
            } finally {
                tempDir.deleteRecursively()
            }
        }

    private fun installIconPackFromDir(dir: File) {
        val iconPackInfo = validateIconPack(dir) ?: return

        val installDir = iconPackDir.child(iconPackInfo.id)
        if (installDir.exists()) {
            uninstallIconPack(iconPackInfo.id)
        }

        dir.copyRecursively(installDir, overwrite = true)

        val iconPack = IconPack(iconPackInfo, installDir)
        iconPacks[iconPackInfo.id] = iconPack
    }

    internal fun validateIconPack(dir: File): IconPackInfo? {
        val iconPackJson = dir.resolve("manifest.json")
        if (!iconPackJson.exists()) {
            toast("Missing manifest.json")
            return null
        }
        val iconPackInfo =
            runCatching { Gson().fromJson(iconPackJson.readText(), IconPackInfo::class.java) }
                .getOrElse {
                    toast("Invalid manifest.json")
                    return null
                }

        return iconPackInfo
    }

    fun uninstallIconPack(iconPackId: IconPackId) {
        val iconPack = iconPacks[iconPackId] ?: return
        iconPack.installDir.deleteRecursively()
        iconPacks.remove(iconPackId)
    }

    suspend fun indexIconPacks() {
        iconPacks.clear()
        withContext(Dispatchers.IO) {
            iconPackDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val manifestJson = dir.resolve("manifest.json")
                    if (manifestJson.exists()) {
                        runCatching {
                            val iconPackInfo = Gson().fromJson(manifestJson.readText(), IconPackInfo::class.java)
                            val installDir = iconPackDir.child(iconPackInfo.id)
                            val iconPack = IconPack(iconPackInfo, installDir)
                            iconPacks[iconPackInfo.id] = iconPack
                        }
                    }
                }
            }
        }

        if (Settings.icon_pack.isNotEmpty()) {
            currentIconPack.value = iconPacks[Settings.icon_pack]
        }
    }
}
