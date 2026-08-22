package com.rk.settings.editor

import com.google.gson.GsonBuilder
import com.rk.settings.Settings
import com.rk.utils.application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object FontRegistry {
    private val _fonts = MutableStateFlow<List<Font>>(emptyList())
    val fonts: StateFlow<List<Font>> = _fonts.asStateFlow()

    data class Font(val name: String, val isAsset: Boolean, val pathOrAsset: String)

    init {
        application!!.assets.list("fonts")?.forEach { asset ->
            if (asset.endsWith(".ttf")) {
                _fonts.update { it + Font(name = asset.removeSuffix(".ttf"), isAsset = true, pathOrAsset = "fonts/$asset") }
            }
        }
        restoreFonts()
    }

    private fun restoreFonts() {
        val f = Settings.font_gson
        val gson = GsonBuilder().create()

        try {
            val restoredFonts: List<Font>? = gson.fromJson(f, Array<Font>::class.java)?.toList()

            _fonts.update { current -> (current + restoredFonts.orEmpty()).distinctBy { it.name } }
        } catch (e: Exception) {
            error("Clear data recommended \n\n $e")
        }
    }

    fun addFont(font: Font) {
        _fonts.update { it + font }
    }

    fun removeFont(font: Font) {
        _fonts.update { it - font }
    }

    fun saveFonts() {
        val gson = GsonBuilder().create()
        val json = gson.toJson(_fonts.value)
        Settings.font_gson = json
    }
}
