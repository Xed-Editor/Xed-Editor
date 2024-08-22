package com.rk.librunner.runners.beanshell

import android.content.Context
import android.graphics.drawable.Drawable
import bsh.Interpreter
import com.rk.libPlugin.server.API
import com.rk.librunner.RunnableInterface
import java.io.File

class BeanshellRunner : RunnableInterface {
    override fun run(file: File, context: Context) {
        val interpreter = Interpreter()
        interpreter.setClassLoader(context.applicationContext.classLoader)
        interpreter.set("app", context.applicationContext)
        interpreter.set("api", API())
        interpreter.source(file)
    }

    override fun getName(): String {
        return "BeanShell 3.0.0-SNAPSHOT"
    }

    override fun getDescription(): String {
        return "BeanShell"
    }

    override fun getIcon(context: Context): Drawable? {
        return null
    }
}