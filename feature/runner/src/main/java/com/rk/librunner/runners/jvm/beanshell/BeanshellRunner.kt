package com.rk.librunner.runners.jvm.beanshell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import bsh.Interpreter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.plugin.server.PluginError
import com.rk.plugin.server.api.API
import com.rk.librunner.RunnerImpl
import java.io.File

class BeanshellRunner : RunnerImpl {

    override fun run(file: File, context: Context) {
        try {
            val interpreter = Interpreter()
            interpreter.setClassLoader(context.applicationContext.classLoader)
            interpreter.set("app", context.applicationContext)
            interpreter.set("api", API.getInstance())
            interpreter.eval("""
                import com.rk.xededitor.MainActivity.*;
                import com.rk.xededitor.*;
                import com.rk.plugin.*;
                import com.rk.plugin.server.*;
                import com.rk.plugin.server.api.*;
                import com.rk.libcommons.*;
                import com.jaredrummler.ktsh.Shell;
            """)
            interpreter.source(file)
        }catch (e:Exception){
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
}