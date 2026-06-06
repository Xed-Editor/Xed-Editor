package com.rk.editor

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import java.io.File

object FontCache {
    private val cachedFonts = mutableMapOf<String, CachedFont>()

    data class CachedFont(val typeface: Typeface, val composeFont: Font)

    fun loadFont(context: Context, path: String, isAsset: Boolean) {
        if (cachedFonts.containsKey(path)) return
        loadFontInternal(context, path, isAsset)
    }

    private fun loadFontInternal(context: Context, path: String, isAsset: Boolean) {
        val font =
            if (isAsset) {
                val typeface = Typeface.createFromAsset(context.assets, path)
                val composeFont = Font(path, context.assets)
                CachedFont(typeface, composeFont)
            } else {
                val file = File(path)
                if (!file.exists()) return
                val typeface = Typeface.createFromFile(file)
                val composeFont = Font(file)
                CachedFont(typeface, composeFont)
            }
        cachedFonts[path] = font
    }

    private fun getOrLoadFont(context: Context, path: String, isAsset: Boolean): CachedFont? {
        val cached = cachedFonts[path]
        if (cached != null) return cached
        loadFontInternal(context, path, isAsset)
        return cachedFonts[path]
    }

    fun getTypeface(context: Context, path: String, isAsset: Boolean): Typeface? {
        return getOrLoadFont(context, path, isAsset)?.typeface
    }

    fun getFont(context: Context, path: String, isAsset: Boolean): Font? {
        return getOrLoadFont(context, path, isAsset)?.composeFont
    }
}
