package com.rk.libPlugin.server

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import bsh.Interpreter
import java.io.File

class Plugin(
    private val api: API,
    val info: Manifest,
    val pluginHome: String,
    val app: Application
) : Thread() {
    lateinit var interpreter: Interpreter
    override fun run() {
        with(info) {
            interpreter = Interpreter()
            interpreter.setClassLoader(app.classLoader)
            interpreter.set("manifest", info)
            interpreter.set("app", app)
            interpreter.set("api", api)
            interpreter.source(File(pluginHome, script))
        }

    }


}