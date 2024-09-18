package com.rk.librunner.runners.jvm.beanshell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import bsh.Interpreter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libPlugin.server.api.API
import com.rk.librunner.RunnerImpl
import java.io.File

class BeanshellRunner : RunnerImpl {

    override fun run(file: File, context: Context) {
        val handler = Handler(Looper.getMainLooper())
        try {
            val interpreter = Interpreter()
            interpreter.setClassLoader(context.applicationContext.classLoader)
            interpreter.set("app", context.applicationContext)
            interpreter.set("api", API.getInstance())
            interpreter.eval("""
                import com.rk.xededitor.MainActivity.*;
                import com.rk.xededitor.*;
                import com.rk.libPlugin.*;
                import com.rk.libPlugin.server.*;
                import com.rk.libPlugin.server.api.*;
                import com.rk.libcommons.*;
                import com.jaredrummler.ktsh.Shell;
            """)
            interpreter.source(file)
        }catch (e:Exception){
            handler.post {
                MaterialAlertDialogBuilder(context).setTitle("Error").setNeutralButton("Copy"){
                        _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("label", e.toString())
                    clipboard.setPrimaryClip(clip)
                }.setPositiveButton("OK", null).setMessage(e.toString()).show()
            }
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