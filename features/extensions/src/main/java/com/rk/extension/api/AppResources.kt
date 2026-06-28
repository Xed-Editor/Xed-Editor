// DO NOT UPDATE PACKAGE NAME OTHERWISE EXTENSIONS WILL BREAK
package com.rk.extension

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import java.io.InputStream

class AppResources(private val context: Context, private val resources: Resources, private val packageName: String) {

    private fun id(name: String, type: String): Int {
        return resources.getIdentifier(name, type, packageName)
    }

    fun getString(name: String): String? {
        val resId = id(name, "string")
        return if (resId != 0) resources.getString(resId) else null
    }

    fun getStringId(name: String): Int? {
        val resId = id(name, "string")
        return if (resId != 0) resId else null
    }

    fun getText(name: String): CharSequence? {
        val resId = id(name, "string")
        return if (resId != 0) resources.getText(resId) else null
    }

    fun getStringArray(name: String): Array<String>? {
        val resId = id(name, "array")
        return if (resId != 0) resources.getStringArray(resId) else null
    }

    fun getTextArray(name: String): Array<CharSequence>? {
        val resId = id(name, "array")
        return if (resId != 0) resources.getTextArray(resId) else null
    }

    fun getIntArray(name: String): IntArray? {
        val resId = id(name, "array")
        return if (resId != 0) resources.getIntArray(resId) else null
    }

    fun getArrayId(name: String): Int? {
        val resId = id(name, "array")
        return if (resId != 0) resId else null
    }

    fun getQuantityString(name: String, quantity: Int): String? {
        val resId = id(name, "plurals")
        return if (resId != 0) resources.getQuantityString(resId, quantity) else null
    }

    fun getQuantityText(name: String, quantity: Int): CharSequence? {
        val resId = id(name, "plurals")
        return if (resId != 0) resources.getQuantityText(resId, quantity) else null
    }

    fun getPluralId(name: String): Int? {
        val resId = id(name, "plurals")
        return if (resId != 0) resId else null
    }

    fun getDrawable(name: String): Drawable? {
        val resId = id(name, "drawable")
        return if (resId != 0) ResourcesCompat.getDrawable(resources, resId, context.theme) else null
    }

    fun getDrawableId(name: String): Int? {
        val resId = id(name, "drawable")
        return if (resId != 0) resId else null
    }

    fun getDimension(name: String): Float? {
        val resId = id(name, "dimen")
        return if (resId != 0) resources.getDimension(resId) else null
    }

    fun getDimensionId(name: String): Int? {
        val resId = id(name, "dimen")
        return if (resId != 0) resId else null
    }

    fun getInteger(name: String): Int? {
        val resId = id(name, "integer")
        return if (resId != 0) resources.getInteger(resId) else null
    }

    fun getIntegerId(name: String): Int? {
        val resId = id(name, "integer")
        return if (resId != 0) resId else null
    }

    fun getBoolean(name: String): Boolean? {
        val resId = id(name, "bool")
        return if (resId != 0) resources.getBoolean(resId) else null
    }

    fun getBooleanId(name: String): Int? {
        val resId = id(name, "bool")
        return if (resId != 0) resId else null
    }

    fun openRaw(name: String): InputStream? {
        val resId = id(name, "raw")
        return if (resId != 0) resources.openRawResource(resId) else null
    }

    fun getRawId(name: String): Int? {
        val resId = id(name, "raw")
        return if (resId != 0) resId else null
    }

    fun getFont(name: String): Typeface? {
        val resId = id(name, "font")
        return if (resId != 0) ResourcesCompat.getFont(context, resId) else null
    }

    fun getFontId(name: String): Int? {
        val resId = id(name, "font")
        return if (resId != 0) resId else null
    }

    fun getAnimationId(name: String): Int? {
        val resId = id(name, "anim")
        return if (resId != 0) resId else null
    }

    fun getAnimatorId(name: String): Int? {
        val resId = id(name, "animator")
        return if (resId != 0) resId else null
    }
}
