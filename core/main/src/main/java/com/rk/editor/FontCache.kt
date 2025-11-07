package com.rk.editor

import android.content.Context
import android.graphics.Typeface
import java.io.File

object FontCache {
    private val cachedFonts = mutableMapOf<String, Typeface>()

    fun loadFont(context: Context, path: String, isAsset: Boolean) {
        try {
            val font = if (isAsset) {
                context.assets.open(path).close()
                Typeface.createFromAsset(context.assets, path)
            } else {
                val file = File(path)
                if (!file.exists()) {
                    return
                }
                Typeface.createFromFile(file)
            }
            cachedFonts[path] = font
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun getFont(context: Context, path: String, isAsset: Boolean): Typeface? {
        return if (cachedFonts.containsKey(path)){
            cachedFonts[path]
        }else{
            try {
                val font = if (isAsset) {
                    Typeface.createFromAsset(context.assets, path)
                } else {
                    Typeface.createFromFile(File(path))
                }
                cachedFonts[path] = font
                font
            } catch (e: Exception) {
                null
            }
        }
    }
}


