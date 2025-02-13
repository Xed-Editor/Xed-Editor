package com.rk.resources

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

typealias drawables = R.drawable
typealias strings = R.string

object Res{
    @JvmField
    var application:Application? = null
}

inline fun Int.getString():String{
    return ContextCompat.getString(Res.application!!, this)
}

inline fun Int.getDrawable():Drawable?{
    return ContextCompat.getDrawable(Res.application!!,this)
}