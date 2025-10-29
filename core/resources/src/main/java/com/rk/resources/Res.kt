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

inline fun Int.getString(context: Context = Res.application!!): String {
    return ContextCompat.getString(context, this)
}

inline fun Int.getDrawable(context: Context): Drawable? {
    return ContextCompat.getDrawable(context, this)
}

inline fun Int.getFilledString(vararg args: Any?): String {
    return this.getString().fillPlaceholders(*args)
}

inline fun Int.getFilledString(context: Context = Res.application!!,vararg args: Any?): String {
    return this.getString(context).fillPlaceholders(*args)
}

inline fun String.fillPlaceholders(vararg args: Any?): String {
    return String.format(this, *args)
}