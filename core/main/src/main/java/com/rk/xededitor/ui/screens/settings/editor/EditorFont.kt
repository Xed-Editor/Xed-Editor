package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.GsonBuilder
import com.rk.libcommons.application
import com.rk.libcommons.toast
import com.rk.settings.Settings


object EditorFont {
    val fonts = mutableStateListOf<Font>()

    data class Font(val name: String, val isAsset: Boolean, val pathOrAsset: String)

    init {
        application!!.assets.list("fonts")?.forEach { asset ->
            if (asset.endsWith(".ttf")) {
                fonts.add(
                    Font(
                        name = asset.removeSuffix(".ttf"),
                        isAsset = true,
                        pathOrAsset = "fonts/$asset"
                    )
                )
            }
        }
        restoreFonts()
    }

    private fun restoreFonts() {
        val f = Settings.font_gson
        val gson = GsonBuilder().create()

        try {
            val restoredFonts: List<Font>? = gson.fromJson(f, Array<Font>::class.java)?.toList()

            restoredFonts?.forEach { font ->
                if (fonts.map { it.name }.contains(font.name).not()) {
                    fonts.add(font)
                }
            }

        } catch (e: Exception) {
            error("Clear data recommended \n\n $e")
        }
    }

    fun saveFonts() {
        val gson = GsonBuilder().create()
        val json = gson.toJson(fonts)
        Settings.font_gson = json
    }
}