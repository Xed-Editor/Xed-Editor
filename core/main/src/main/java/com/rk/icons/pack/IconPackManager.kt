package com.rk.icons.pack

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.pm.PackageInfoCompat
import com.rk.activities.settings.SettingsActivity
import com.rk.file.child
import com.rk.file.createDirIfNot
import com.rk.file.localDir
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.application
import com.rk.utils.dialog
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json

val currentIconPack = mutableStateOf<IconPack?>(null)
val iconPackDir = localDir().child("icon_pack").also { it.createDirIfNot() }

class IconPackManager(private val context: Application) {
    val iconPacks = mutableStateMapOf<IconPackId, IconPack>()
    val json = Json { ignoreUnknownKeys = true }

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
        val iconPackManifest = validateIconPack(dir) ?: return

        val packageName = application!!.packageName
        val packageManager = application!!.packageManager
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        if (iconPackManifest.minAppVersion != null && iconPackManifest.minAppVersion.toLong() > currentVersionCode) {
            dialog(
                context = SettingsActivity.instance,
                title = strings.warning.getString(),
                msg = strings.incompatible_theme_warning.getString(),
                cancelString = strings.cancel,
                okString = strings.continue_action,
                onOk = { writeIconPackToDisk(iconPackManifest, dir) },
            )
            return
        }

        writeIconPackToDisk(iconPackManifest, dir)
    }

    private fun writeIconPackToDisk(iconPackManifest: IconPackManifest, dir: File) {
        val installDir = iconPackDir.child(iconPackManifest.id)
        if (installDir.exists()) {
            uninstallIconPack(iconPackManifest.id)
        }

        dir.copyRecursively(installDir, overwrite = true)

        val iconPack = IconPack(iconPackManifest, installDir)
        iconPacks[iconPackManifest.id] = iconPack
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun validateIconPack(dir: File): IconPackManifest? {
        val iconPackJson = dir.resolve("manifest.json")
        if (!iconPackJson.exists()) {
            dialog(
                SettingsActivity.instance,
                strings.icon_pack_install_failed.getString(),
                strings.manifest_missing.getString(),
                cancelable = false,
            )

            return null
        }
        val iconPackManifest =
            runCatching { json.decodeFromString<IconPackManifest>(iconPackJson.readText()) }
                .getOrElse { e ->
                    if (e is MissingFieldException) {
                        val fields = e.missingFields.joinToString("\n") { "• $it" }
                        dialog(
                            SettingsActivity.instance,
                            strings.icon_pack_install_failed.getString(),
                            strings.manifest_missing_fields.getFilledString(fields),
                            cancelable = false,
                        )
                        return null
                    }
                    dialog(
                        SettingsActivity.instance,
                        strings.icon_pack_install_failed.getString(),
                        e.localizedMessage ?: strings.unknown_err.getString(),
                        cancelable = false,
                    )
                    return null
                }

        return iconPackManifest
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
                            val iconPackManifest = json.decodeFromString<IconPackManifest>(manifestJson.readText())
                            val installDir = iconPackDir.child(iconPackManifest.id)
                            val iconPack = IconPack(iconPackManifest, installDir)
                            iconPacks[iconPackManifest.id] = iconPack
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
