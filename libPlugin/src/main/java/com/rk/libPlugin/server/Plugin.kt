package com.rk.libPlugin.server

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import bsh.Interpreter
import java.io.File

class Plugin(val info: Manifest, val pluginHome:String, val app: Application) : Thread() {
    lateinit var interpreter: Interpreter
    val handler = Handler(Looper.getMainLooper())
    override fun run() {
        with(info) {
            try {
                interpreter = Interpreter()
                interpreter.set("manifest", info)
                interpreter.set("app", app)
                interpreter.source(File(pluginHome,script))
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    Toast.makeText(
                        app,
                        "an error occurred on plugin \"$name\" see logcat for more info",
                        Toast.LENGTH_LONG
                    ).show()
                }

            }
        }

    }


}