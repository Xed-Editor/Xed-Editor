package com.rk.resources

import android.app.Application
import androidx.core.content.ContextCompat

typealias drawables = R.drawable
typealias strings = R.string

object Res{
    var context:Application? = null
}

inline fun Int.getString():String{
    return  ContextCompat.getString(Res.context!!, this)
}