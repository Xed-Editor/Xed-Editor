package com.rk.libplugin

import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class Server : Thread() {
    override fun run() {
        val scriptEngine = BasicJvmScriptingHost()
        val script = "println(\"hello from kts\")"

        val compilationConfiguration = ScriptCompilationConfiguration {
            jvm {
                dependenciesFromClassContext(Server::class, wholeClasspath = true)
            }
        }

        val evaluationConfiguration = ScriptEvaluationConfiguration {
            //providedProperties(Pair("arg1", "value1"))
        }

        val result = scriptEngine.eval(
            StringScriptSource(script),
            compilationConfiguration,
            evaluationConfiguration
        )



    }
}
