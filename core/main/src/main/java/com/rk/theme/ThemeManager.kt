package com.rk.theme

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.toColorInt
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.activities.settings.SettingsActivity
import com.rk.common.XedPackage
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.PackageCache
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.application
import com.rk.utils.dialogRes
import com.rk.utils.errorDialog
import com.rk.utils.logError
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.util.Properties
import kotlinx.serialization.json.JsonElement as KJsonElement

@Serializable
data class ThemeEntry(
    val id: String,
    val manifest: ThemeManifest,
    val size: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

class ThemeManager(private val context: Application) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    val loadedThemes = mutableStateListOf<ThemeHolder>().apply { addAll(builtInThemes) }
    val localThemes = mutableStateMapOf<String, LocalTheme>()
    val storeThemes = mutableStateMapOf<String, StoreTheme>()

    fun isInstalled(id: String) = localThemes.containsKey(id)

    fun getTheme(id: String): ThemePackage? {
        val local = localThemes[id]
        val store = storeThemes[id]

        return when {
            (local != null && store != null) -> UpdatableTheme(local, store)
            local != null -> local
            store != null -> store
            else -> null
        }
    }

    fun getSyncedThemes(): List<ThemePackage> {
        val allIds = localThemes.keys + storeThemes.keys
        return allIds.mapNotNull { getTheme(it) }
    }

    fun uninstallTheme(theme: ThemeHolder) {
        val localTheme = localThemes[theme.id] ?: return
        File(localTheme.installPath).deleteRecursively()

        loadedThemes.remove(theme)
    }

    private suspend fun calcSize(dir: File): Long {
        return FileOperations.calculateContent(FileWrapper(dir)).totalSize
    }

    private fun resolveCache(dir: File): PackageCache {
        val cacheFile = dir.resolve("cache.json")

        if (!cacheFile.exists() || !cacheFile.isFile) {
            return PackageCache()
        }

        return runCatching {
            json.decodeFromString<PackageCache>(cacheFile.readText())
        }
            .getOrElse {
                PackageCache()
            }
    }

    private fun writeCache(dir: File, cache: PackageCache) {
        val cacheFile = dir.resolve("cache.json")
        cacheFile.writeText(json.encodeToString(cache))
    }

    suspend fun invalidateSize(pkg: ThemePackage) {
        if (pkg is StoreTheme) return

        withContext(Dispatchers.IO) {
            val dir = themeDir().resolve(pkg.id)
            val cache = resolveCache(dir)
            val newSize = calcSize(dir)
            writeCache(dir, cache.copy(size = newSize))

            withContext(Dispatchers.Main) {
                localThemes[pkg.id]?.size = newSize
            }
        }
    }

    suspend fun installTheme(file: File) {
        withContext(Dispatchers.IO) {
            val tempDir = File(application!!.cacheDir, "theme_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                if (file.extension == "json") {
                    // Legacy single-file JSON
                    val manifest = validateManifestJson(file.readText())
                    manifest?.let {
                        installThemeFromData(it, null)
                    }
                    return@withContext
                }

                XedPackage.extract(file, tempDir)

                val manifestFile = File(tempDir, "manifest.json")
                val themeFile = File(tempDir, "theme.json")

                val jsonText =
                    when {
                        manifestFile.exists() -> manifestFile.readText()
                        themeFile.exists() -> themeFile.readText()
                        else -> {
                            withContext(Dispatchers.Main) { toast("Neither manifest.json nor theme.json found") }
                            return@withContext
                        }
                    }

                jsonText.let {
                    val manifest = validateManifestJson(it) ?: return@let
                    installThemeFromData(manifest, tempDir)
                }
            } catch (e: Exception) {
                errorDialog(e)
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun validateManifestJson(text: String): ThemeManifest? {
        return runCatching {
            json.decodeFromString<ThemeManifest>(text)
        }
            .getOrElse { e ->
                if (e is MissingFieldException) {
                    val fields = e.missingFields.joinToString("\n") { "• $it" }
                    dialogRes(
                        SettingsActivity.instance,
                        strings.theme_install_failed.getString(),
                        strings.manifest_missing_fields.getFilledString(fields),
                        cancelable = false,
                    )
                    return null
                }
                dialogRes(
                    SettingsActivity.instance,
                    strings.theme_install_failed.getString(),
                    e.localizedMessage ?: strings.unknown_err.getString(),
                    cancelable = false,
                )
                return null
            }
    }

    private suspend fun installThemeFromData(manifest: ThemeManifest, sourceDir: File?) =
        withContext(Dispatchers.IO) {
            val packageName = application!!.packageName
            val packageManager = application!!.packageManager
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

            if (manifest.minAppVersion != null && manifest.minAppVersion.toLong() > currentVersionCode) {
                dialogRes(
                    activity = SettingsActivity.instance,
                    title = strings.warning.getString(),
                    msg = strings.incompatible_theme_warning.getString(),
                    cancelRes = strings.cancel,
                    okRes = strings.continue_action,
                    onOk = {
                        launch(Dispatchers.IO) {
                            finishThemeInstall(manifest, sourceDir)
                            indexLocalThemes()
                        }
                    },
                )
                return@withContext
            }

            finishThemeInstall(manifest, sourceDir)
            indexLocalThemes()
        }

    private suspend fun finishThemeInstall(manifest: ThemeManifest, sourceDir: File?) {
        val installDir = themeDir().child(manifest.id).also { if (!it.exists()) it.mkdirs() }

        var oldCreatedAt: Long? = null
        if (installDir.exists()) {
            oldCreatedAt = resolveCache(installDir).createdAt
        }

        val manifestFile = installDir.resolve("manifest.json")
        manifestFile.writeText(json.encodeToString<ThemeManifest>(manifest))

        sourceDir?.listFiles()?.forEach { file ->
            if (file.name != "manifest.json") {
                file.copyRecursively(installDir.resolve(file.name), overwrite = true)
            }
        }

        val size = calcSize(installDir)
        val newCache =
            PackageCache(
                createdAt = oldCreatedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                size = size,
            )
        writeCache(installDir, newCache)
    }

    suspend fun indexStoreThemes() =
        withContext(Dispatchers.IO) {
            val themesList = runCatching { StoreManager.fetchThemes() }.getOrNull() ?: return@withContext
            val newThemes = themesList.associateBy({ it.id }, { StoreTheme(it) })
            withContext(Dispatchers.Main) {
                storeThemes.clear()
                storeThemes.putAll(newThemes)
            }
        }

    suspend fun indexLocalThemes() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val themeDir = themeDir()
            if (!themeDir.exists()) return@withContext

            migrateOldThemes(themeDir)

            val newLocalThemes = mutableMapOf<String, LocalTheme>()
            val newLoadedThemes = mutableListOf<ThemeHolder>()
            themeDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    runCatching {
                        val manifestFile = dir.resolve("manifest.json")
                        if (manifestFile.exists()) {
                            val manifest = json.decodeFromString<ThemeManifest>(manifestFile.readText())
                            newLoadedThemes.add(manifest.build())

                            val cache = resolveCache(dir)
                            val size = cache.size ?: calcSize(dir).also { writeCache(dir, cache.copy(size = it)) }

                            val theme =
                                LocalTheme(
                                    manifest = manifest,
                                    installPath = dir.absolutePath,
                                    createdAt = cache.createdAt,
                                    updatedAt = cache.updatedAt,
                                    initSize = size,
                                )

                            newLocalThemes[manifest.id] = theme
                        }
                    }
                        .onFailure {
                            logError(it, "Failed to index local themes")
                        }
                }
            }
            withContext(Dispatchers.Main) {
                localThemes.clear()
                localThemes.putAll(newLocalThemes)

                loadedThemes.clear()
                loadedThemes.addAll(builtInThemes)
                loadedThemes.addAll(newLoadedThemes)
            }
        }
    }

    @Deprecated("Migration from old theme format for backwards compatibility")
    private suspend fun migrateOldThemes(themeDir: File) {
        val listFiles = themeDir.listFiles()
        var migratedCount = 0
        listFiles?.forEach { file ->
            if (file.isFile) {
                runCatching {
                    if (migratedCount == 0) {
                        withContext(Dispatchers.Main) {
                            toast(strings.migrating_themes.getString())
                        }
                    }
                    ObjectInputStream(FileInputStream(file)).use { input ->
                        val oldConfig = input.readObject()
                        if (oldConfig is ThemeConfig) {
                            val manifest =
                                ThemeManifest(
                                    id = oldConfig.id ?: file.name,
                                    name = oldConfig.name ?: file.name,
                                    minAppVersion = oldConfig.minAppVersion,
                                    inheritBase = oldConfig.inheritBase ?: true,
                                    light = oldConfig.light?.let { ThemePaletteNew.fromLegacyPalette(it) },
                                    dark = oldConfig.dark?.let { ThemePaletteNew.fromLegacyPalette(it) },
                                )

                            finishThemeInstall(manifest, null)
                            migratedCount++
                            file.delete()
                        }
                    }
                }
                    .onFailure {
                        file.delete()
                    }
            }
        }

        if (migratedCount > 0) {
            withContext(Dispatchers.Main) {
                toast(strings.theme_migrated.getFilledString(migratedCount))
            }
        }
    }

    private fun String.toColor(): Color {
        return try {
            Color(this.toColorInt())
        } catch (_: Exception) {
            toast("Invalid color: $this")
            Color.Unspecified
        }
    }

    fun ThemeManifest.build(): ThemeHolder {
        fun Map<String, String>.toProperties(): Properties {
            val props = Properties()
            for ((k, v) in this) props[k] = v
            return props
        }

        val lightTokenColors = light?.tokenColors.toTokenColorArray()
        val darkTokenColors = dark?.tokenColors.toTokenColorArray()

        return ThemeHolder(
            id = id,
            name = name,
            inheritBase = inheritBase,
            lightScheme = light?.build(isDarkTheme = false) ?: blueberry.lightScheme,
            darkScheme = dark?.build(isDarkTheme = true) ?: blueberry.darkScheme,
            lightTerminalColors = light?.terminalColors?.toProperties() ?: Properties(),
            darkTerminalColors = dark?.terminalColors?.toProperties() ?: Properties(),
            lightEditorColors = mapEditorColorScheme(light?.editorColors),
            darkEditorColors = mapEditorColorScheme(dark?.editorColors),
            lightTokenColors = lightTokenColors,
            darkTokenColors = darkTokenColors,
        )
    }

    private fun KJsonElement?.toTokenColorArray(): JsonArray {
        if (this == null) return JsonArray()

        val gsonElement = JsonParser.parseString(this.toString())
        if (gsonElement.isJsonArray) return gsonElement.asJsonArray

        if (gsonElement.isJsonObject) {
            val convertedArray = JsonArray()
            for ((scope, colorHex) in gsonElement.asJsonObject.entrySet()) {
                val item =
                    JsonObject().apply {
                        addProperty("scope", scope)
                        val settings = JsonObject()
                        settings.addProperty("foreground", colorHex.asString)
                        add("settings", settings)
                    }
                convertedArray.add(item)
            }
            return convertedArray
        }

        return JsonArray()
    }

    fun ThemePaletteNew.build(isDarkTheme: Boolean): ColorScheme {
        return if (isDarkTheme) {
            darkColorScheme(
                primary = baseColors?.primary?.toColor() ?: blueberry.darkScheme.primary,
                onPrimary = baseColors?.onPrimary?.toColor() ?: blueberry.darkScheme.onPrimary,
                primaryContainer = baseColors?.primaryContainer?.toColor() ?: blueberry.darkScheme.primaryContainer,
                onPrimaryContainer =
                    baseColors?.onPrimaryContainer?.toColor() ?: blueberry.darkScheme.onPrimaryContainer,
                secondary = baseColors?.secondary?.toColor() ?: blueberry.darkScheme.secondary,
                onSecondary = baseColors?.onSecondary?.toColor() ?: blueberry.darkScheme.onSecondary,
                secondaryContainer =
                    baseColors?.secondaryContainer?.toColor() ?: blueberry.darkScheme.secondaryContainer,
                onSecondaryContainer =
                    baseColors?.onSecondaryContainer?.toColor() ?: blueberry.darkScheme.onSecondaryContainer,
                tertiary = baseColors?.tertiary?.toColor() ?: blueberry.darkScheme.tertiary,
                onTertiary = baseColors?.onTertiary?.toColor() ?: blueberry.darkScheme.onTertiary,
                tertiaryContainer = baseColors?.tertiaryContainer?.toColor() ?: blueberry.darkScheme.tertiaryContainer,
                onTertiaryContainer =
                    baseColors?.onTertiaryContainer?.toColor() ?: blueberry.darkScheme.onTertiaryContainer,
                error = baseColors?.error?.toColor() ?: blueberry.darkScheme.error,
                onError = baseColors?.onError?.toColor() ?: blueberry.darkScheme.onError,
                errorContainer = baseColors?.errorContainer?.toColor() ?: blueberry.darkScheme.errorContainer,
                onErrorContainer = baseColors?.onErrorContainer?.toColor() ?: blueberry.darkScheme.onErrorContainer,
                background = baseColors?.background?.toColor() ?: blueberry.darkScheme.background,
                onBackground = baseColors?.onBackground?.toColor() ?: blueberry.darkScheme.onBackground,
                surface = baseColors?.surface?.toColor() ?: blueberry.darkScheme.surface,
                onSurface = baseColors?.onSurface?.toColor() ?: blueberry.darkScheme.onSurface,
                surfaceVariant = baseColors?.surfaceVariant?.toColor() ?: blueberry.darkScheme.surfaceVariant,
                onSurfaceVariant = baseColors?.onSurfaceVariant?.toColor() ?: blueberry.darkScheme.onSurfaceVariant,
                outline = baseColors?.outline?.toColor() ?: blueberry.darkScheme.outline,
                outlineVariant = baseColors?.outlineVariant?.toColor() ?: blueberry.darkScheme.outlineVariant,
                scrim = baseColors?.scrim?.toColor() ?: blueberry.darkScheme.scrim,
                inverseSurface = baseColors?.inverseSurface?.toColor() ?: blueberry.darkScheme.inverseSurface,
                inverseOnSurface = baseColors?.inverseOnSurface?.toColor() ?: blueberry.darkScheme.inverseOnSurface,
                inversePrimary = baseColors?.inversePrimary?.toColor() ?: blueberry.darkScheme.inversePrimary,
                surfaceTint = baseColors?.surfaceTint?.toColor() ?: blueberry.darkScheme.surfaceTint,
                surfaceDim = baseColors?.surfaceDim?.toColor() ?: blueberry.darkScheme.surfaceDim,
                surfaceBright = baseColors?.surfaceBright?.toColor() ?: blueberry.darkScheme.surfaceBright,
                surfaceContainerLowest =
                    baseColors?.surfaceContainerLowest?.toColor() ?: blueberry.darkScheme.surfaceContainerLowest,
                surfaceContainerLow =
                    baseColors?.surfaceContainerLow?.toColor() ?: blueberry.darkScheme.surfaceContainerLow,
                surfaceContainer = baseColors?.surfaceContainer?.toColor() ?: blueberry.darkScheme.surfaceContainer,
                surfaceContainerHigh =
                    baseColors?.surfaceContainerHigh?.toColor() ?: blueberry.darkScheme.surfaceContainerHigh,
                surfaceContainerHighest =
                    baseColors?.surfaceContainerHighest?.toColor() ?: blueberry.darkScheme.surfaceContainerHighest,
            )
        } else {
            lightColorScheme(
                primary = baseColors?.primary?.toColor() ?: blueberry.lightScheme.primary,
                onPrimary = baseColors?.onPrimary?.toColor() ?: blueberry.lightScheme.onPrimary,
                primaryContainer = baseColors?.primaryContainer?.toColor() ?: blueberry.lightScheme.primaryContainer,
                onPrimaryContainer =
                    baseColors?.onPrimaryContainer?.toColor() ?: blueberry.lightScheme.onPrimaryContainer,
                secondary = baseColors?.secondary?.toColor() ?: blueberry.lightScheme.secondary,
                onSecondary = baseColors?.onSecondary?.toColor() ?: blueberry.lightScheme.onSecondary,
                secondaryContainer =
                    baseColors?.secondaryContainer?.toColor() ?: blueberry.lightScheme.secondaryContainer,
                onSecondaryContainer =
                    baseColors?.onSecondaryContainer?.toColor() ?: blueberry.lightScheme.onSecondaryContainer,
                tertiary = baseColors?.tertiary?.toColor() ?: blueberry.lightScheme.tertiary,
                onTertiary = baseColors?.onTertiary?.toColor() ?: blueberry.lightScheme.onTertiary,
                tertiaryContainer = baseColors?.tertiaryContainer?.toColor() ?: blueberry.lightScheme.tertiaryContainer,
                onTertiaryContainer =
                    baseColors?.onTertiaryContainer?.toColor() ?: blueberry.lightScheme.onTertiaryContainer,
                error = baseColors?.error?.toColor() ?: blueberry.lightScheme.error,
                onError = baseColors?.onError?.toColor() ?: blueberry.lightScheme.onError,
                errorContainer = baseColors?.errorContainer?.toColor() ?: blueberry.lightScheme.errorContainer,
                onErrorContainer = baseColors?.onErrorContainer?.toColor() ?: blueberry.lightScheme.onErrorContainer,
                background = baseColors?.background?.toColor() ?: blueberry.lightScheme.background,
                onBackground = baseColors?.onBackground?.toColor() ?: blueberry.lightScheme.onBackground,
                surface = baseColors?.surface?.toColor() ?: blueberry.lightScheme.surface,
                onSurface = baseColors?.onSurface?.toColor() ?: blueberry.lightScheme.onSurface,
                surfaceVariant = baseColors?.surfaceVariant?.toColor() ?: blueberry.lightScheme.surfaceVariant,
                onSurfaceVariant = baseColors?.onSurfaceVariant?.toColor() ?: blueberry.lightScheme.onSurfaceVariant,
                outline = baseColors?.outline?.toColor() ?: blueberry.lightScheme.outline,
                outlineVariant = baseColors?.outlineVariant?.toColor() ?: blueberry.lightScheme.outlineVariant,
                scrim = baseColors?.scrim?.toColor() ?: blueberry.lightScheme.scrim,
                inverseSurface = baseColors?.inverseSurface?.toColor() ?: blueberry.lightScheme.inverseSurface,
                inverseOnSurface = baseColors?.inverseOnSurface?.toColor() ?: blueberry.lightScheme.inverseOnSurface,
                inversePrimary = baseColors?.inversePrimary?.toColor() ?: blueberry.lightScheme.inversePrimary,
                surfaceTint = baseColors?.surfaceTint?.toColor() ?: blueberry.lightScheme.surfaceTint,
                surfaceDim = baseColors?.surfaceDim?.toColor() ?: blueberry.lightScheme.surfaceDim,
                surfaceBright = baseColors?.surfaceBright?.toColor() ?: blueberry.lightScheme.surfaceBright,
                surfaceContainerLowest =
                    baseColors?.surfaceContainerLowest?.toColor() ?: blueberry.lightScheme.surfaceContainerLowest,
                surfaceContainerLow =
                    baseColors?.surfaceContainerLow?.toColor() ?: blueberry.lightScheme.surfaceContainerLow,
                surfaceContainer = baseColors?.surfaceContainer?.toColor() ?: blueberry.lightScheme.surfaceContainer,
                surfaceContainerHigh =
                    baseColors?.surfaceContainerHigh?.toColor() ?: blueberry.lightScheme.surfaceContainerHigh,
                surfaceContainerHighest =
                    baseColors?.surfaceContainerHighest?.toColor() ?: blueberry.lightScheme.surfaceContainerHighest,
            )
        }
    }
}
