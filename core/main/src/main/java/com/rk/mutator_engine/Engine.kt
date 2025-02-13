package com.rk.mutator_engine

import app.cash.quickjs.QuickJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.lang.reflect.Constructor

class Engine(private val javaScript: String, val scope: CoroutineScope) {
    lateinit var quickJS: QuickJs

    fun startWithScope(
        onResult: (QuickJs, Any?) -> Unit, onError: (Throwable) -> Unit,  api: Class<out EngineAPI>
    ) {
        scope.launch { start(onResult, onError, api) }
    }

    suspend fun start(
        onResult: (QuickJs, Any?) -> Unit, onError: (Throwable) -> Unit, api: Class<out EngineAPI>
    ) {
        withContext(Dispatchers.IO) {
            quickJS = QuickJs.create()
            runCatching {
                // Use reflection to create the ImplAPI instance
                val implAPI = createImplAPIWithReflection(this@Engine,api)

                quickJS.set(
                    "api", EngineAPI::class.java, implAPI
                )

                quickJS.evaluate(buildString {
                    EngineAPI::class.java.methods.forEach { method ->
                        append("globalThis.${method.name} = api.${method.name}.bind(api);\n")
                    }
                })

                onResult.invoke(quickJS, quickJS.evaluate(javaScript))
                quickJS.close()
            }.onFailure {
                quickJS.close()
                onError.invoke(it)
            }
        }
    }

    private fun createImplAPIWithReflection(quickJs: Engine,clazz:Class<out EngineAPI>): EngineAPI {
        val constructor: Constructor<*> = clazz.getConstructor(Engine::class.java)
        return constructor.newInstance(quickJs) as EngineAPI
    }
}
