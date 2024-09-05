package com.rk.libPlugin.server

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import bsh.Interpreter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libPlugin.server.api.API
import java.io.File

class Plugin(
    val info: Manifest,
    private val pluginHome: String,
    val app: Application
) : Thread() {
    lateinit var interpreter: Interpreter
    override fun run() {
        try {
            with(info) {
                interpreter = Interpreter()
                interpreter.setClassLoader(app.classLoader)
                interpreter.set("manifest", info)
                interpreter.set("app", app)
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

                interpreter.source(File(pluginHome, script))
            }
        }catch (e:Exception){
            API.runOnUiThread{
                API.getMainActivity()
                    ?.let { MaterialAlertDialogBuilder(it).setTitle("Error").setNeutralButton("Copy"){
                            _, _ ->
                        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("label", e.toString())
                        clipboard.setPrimaryClip(clip)
                    }.setPositiveButton("OK", null).setMessage(e.toString()).show() }
            }
        }


    }


}