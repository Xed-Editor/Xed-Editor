package com.rk.resources

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.DelicateCoroutinesApi

typealias drawables = R.drawable

typealias strings = R.string

typealias plurals = R.plurals

@OptIn(DelicateCoroutinesApi::class)
object Res {
    @JvmField var application: Application? = null
}

inline fun Int.getString(context: Context = Res.application!!): String {
    return ContextCompat.getString(context, this)
}

inline fun Int.getFilledString(vararg args: Any?, context: Context = Res.application!!): String {
    return this.getString(context).fillPlaceholders(*args)
}

inline fun String.fillPlaceholders(vararg args: Any?): String {
    return String.format(this, *args)
}

inline fun Int.getDrawable(context: Context = Res.application!!): Drawable? {
    return ContextCompat.getDrawable(context, this)
}

inline fun Int.getQuantityString(quantity: Int, vararg formatArgs: Any?, context: Context = Res.application!!): String {
    return context.resources.getQuantityString(this, quantity, *formatArgs)
}
