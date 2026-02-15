package com.rk.editor

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.rk.settings.Settings
import com.rk.theme.currentTheme
import com.rk.utils.isDarkTheme
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

object ThemeManager {
    private val colorSchemeCache = hashMapOf<String, TextMateColorScheme>()

    suspend fun createColorScheme(context: Context): TextMateColorScheme {
        val cacheKey = getCacheKey(context)

        colorSchemeCache[cacheKey]?.let {
            return it
        }

        val darkTheme = isDarkTheme(context)
        val amoled = Settings.amoled

        val themeModel =
            when {
                darkTheme && amoled ->
                    buildThemeModel(
                        context = context,
                        basePath = TEXTMATE_AMOLED_PREFIX + DARCULA_THEME,
                        baseName = DARCULA_THEME,
                        darkTheme = true,
                    )
                darkTheme ->
                    buildThemeModel(
                        context = context,
                        basePath = TEXTMATE_PREFIX + DARCULA_THEME,
                        baseName = DARCULA_THEME,
                        darkTheme = true,
                    )
                else ->
                    buildThemeModel(
                        context = context,
                        basePath = TEXTMATE_PREFIX + QUIETLIGHT_THEME,
                        baseName = QUIETLIGHT_THEME,
                        darkTheme = false,
                    )
            }

        TextMateColorScheme.create(themeModel).also {
            colorSchemeCache[cacheKey] = it
            return it
        }
    }

    fun createColorSchemeBlocking(context: Context): TextMateColorScheme = runBlocking { createColorScheme(context) }

    /**
     * Build a [io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel] by applying the user's theme onto a
     * base theme that serves as a fallback.
     *
     * The method:
     * 1. Reads the JSON base theme file from the specified asset path.
     * 2. Merges additional token colors from the currently selected app theme.
     * 3. Converts the modified JSON back into a byte stream and builds a
     *    [io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel].
     *
     * @param context The application context.
     * @param basePath The relative path of the base theme file inside the app's assets directory.
     * @param baseName The logical name of the theme (used when creating the
     *   [io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel]).
     * @param darkTheme Whether to apply the dark variant of the current app theme’s token colors.
     * @return A [io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel] representing the base theme (as a
     *   fallback) merged with the user's theme.
     */
    private suspend fun buildThemeModel(context: Context, basePath: String, baseName: String, darkTheme: Boolean) =
        withContext(Dispatchers.IO) {
            val inputStream = context.assets.open(basePath)
            InputStreamReader(inputStream).use { reader ->
                val jsonElement = JsonParser.parseReader(reader)
                val jsonObject = jsonElement.asJsonObject

                val selectedTheme = currentTheme.value
                val tokenArray =
                    when {
                        selectedTheme == null -> JsonArray()
                        darkTheme -> selectedTheme.darkTokenColors
                        else -> selectedTheme.lightTokenColors
                    }

                // In some TextMate theme files the token colors are saved in an array
                // called settings and in some it's called tokenColors
                val arrayName =
                    if (jsonObject.has("settings")) {
                        "settings"
                    } else if (jsonObject.has("tokenColors")) {
                        "tokenColors"
                    } else null

                selectedTheme?.let { jsonObject.add("name", JsonPrimitive(it.name)) }

                if (!tokenArray.isEmpty) {
                    if (arrayName != null) {
                        if (selectedTheme!!.inheritBase) {
                            val existingTokenColors = jsonObject[arrayName].asJsonArray
                            existingTokenColors.addAll(tokenArray)
                        } else {
                            jsonObject.remove(arrayName)
                            jsonObject.add(arrayName, tokenArray)
                        }
                    } else {
                        jsonObject.add("tokenColors", tokenArray)
                    }
                }

                val bytes = jsonObject.toString().toByteArray(Charsets.UTF_8)
                val bais = ByteArrayInputStream(bytes)
                ThemeModel(IThemeSource.fromInputStream(bais, baseName, null))
            }
        }
}
