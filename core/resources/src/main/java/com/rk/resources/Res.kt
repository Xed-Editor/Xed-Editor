package com.rk.resources

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.text.replace

typealias drawables = R.drawable
typealias strings = R.string

@OptIn(DelicateCoroutinesApi::class)
object Res {
    @JvmField
    var application: Application? = null
}

inline fun Int.getString(): String {
    return ContextCompat.getString(Res.application!!, this)
}

inline fun Int.getDrawable(context: Context): Drawable? {
    return ContextCompat.getDrawable(context, this)
}

inline fun Int.getFilledString(values: Map<String, Any>): String {
    return this.getString().fillPlaceholders(values)
}

fun String.fillPlaceholders(values: Map<String, Any>): String {
    var result = this

    values.forEach { (key, value) ->
        val escapedKey = Regex.escape(key)
        result = result.replace(Regex("%\\($escapedKey\\)[a-z]?"), value.toString())
    }

    return result
}