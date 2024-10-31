package com.rk.runner.runners.jvm.beanshell

import android.content.Context
import android.graphics.drawable.Drawable
import bsh.Interpreter
import com.rk.plugin.server.PluginError
import com.rk.plugin.server.api.API
import com.rk.runner.RunnerImpl
import java.io.File

class BeanshellRunner : RunnerImpl {

    override fun run(file: File, context: Context) {
        try {
            val interpreter = Interpreter()
            interpreter.setClassLoader(context.applicationContext.classLoader)
            interpreter.set("app", context.applicationContext)
            interpreter.set("api", API.getInstance())
            interpreter.eval(
                """
                import com.rk.xededitor.MainActivity.*;
                import com.rk.xededitor.*;
                import com.rk.plugin.*;
                import com.rk.plugin.server.*;
                import com.rk.plugin.server.api.*;
                import com.rk.libcommons.*;
                import com.jaredrummler.ktsh.Shell;
            """
            )
            interpreter.source(file)
        } catch (e: Exception) {
            PluginError.showError(e)
        }
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
    override fun isRunning(): Boolean {
        return false
    }
    override fun stop() {
        TODO("Not yet implemented")
    }
}
