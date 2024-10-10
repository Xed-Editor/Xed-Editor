package com.rk.plugin.server

import android.app.Application
import bsh.Interpreter
import com.rk.plugin.server.api.API
import java.io.File

class Plugin(val info: PluginInfo, val pluginHome: String, val app: Application) : Thread() {

    private lateinit var interpreter: Interpreter

    override fun run() {
        try {
            with(info) {
                if (script == null) {
                    throw RuntimeException("Tried to run a plugin without a script")
                }
                if (File(pluginHome, script).exists().not()) {
                    throw RuntimeException("Script : $script does not exist")
                }
                interpreter = Interpreter()
                interpreter.setClassLoader(app.classLoader)
                interpreter.set("info", info)
                interpreter.set("home", pluginHome)
                interpreter.set("app", app)
                interpreter.set("api", API.getInstance())
                interpreter.eval(
                    """
                import com.rk.xededitor.MainActivity.*;
                import com.rk.xededitor.*;
                import com.rk.plugin.*;
                import com.rk.plugin.server.*;
                import com.rk.plugin.server.api.*;
                import com.rk.plugin.server.api.PluginLifeCycle;
                import com.rk.libcommons.*;
                import android.app.Activity;
                import com.jaredrummler.ktsh.Shell;"""
                )

                interpreter.source(File(pluginHome, script))
            }
        } catch (e: Exception) {
            PluginError.showError(e)
        }
    }
}
