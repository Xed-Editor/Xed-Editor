package com.rk.librunner

import android.content.Context
import android.graphics.drawable.Drawable
import java.io.File

interface RunnableInterface {
    fun run(file: File, context:Context)
    fun getName() : String
    fun getDescription():String
    fun getIcon(context: Context):Drawable?
}