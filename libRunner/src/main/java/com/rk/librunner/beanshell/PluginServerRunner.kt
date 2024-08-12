package com.rk.librunner.beanshell

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.librunner.RunnableInterface
import java.io.File

class PluginServerRunner : RunnableInterface {
    override fun run(file: File, context: Context) {

    }

    override fun getName(): String {
        return "PluginServer"
    }

    override fun getDescription(): String {
        return "Beanshell with PluginAPI"
    }

    override fun getIcon(context: Context): Drawable? {
        return null
    }
}