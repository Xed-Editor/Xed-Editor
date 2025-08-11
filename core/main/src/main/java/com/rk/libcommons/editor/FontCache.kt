package com.rk.libcommons.editor

import android.content.Context
import android.graphics.Typeface
import com.rk.libcommons.errorDialog
import com.rk.settings.Settings
import java.io.File

object FontCache {
    private val cachedFonts = mutableMapOf<String, Typeface>()

    fun loadFont(context: Context, path: String, isAsset: Boolean){
        val font = if (isAsset) {
            Typeface.createFromAsset(context.assets, path)
        } else {
            Typeface.createFromFile(File(path))
        }
        cachedFonts[path] = font
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


