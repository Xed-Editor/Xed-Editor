package com.rk.scriptingengine

import android.util.Log
import android.widget.Toast
import app.cash.quickjs.QuickJs
import com.rk.libcommons.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class JavaScript(private val javaScript: String, private val scope:CoroutineScope){

    fun startWithScope(onResult:(QuickJs,Any?)->Unit,onError:(Throwable)->Unit,api:JavaScriptAPI){
        scope.launch { start(onResult,onError,api) }
    }

    suspend fun start(onResult:(QuickJs,Any?)->Unit,onError:(Throwable)->Unit,api:JavaScriptAPI) {
        withContext(Dispatchers.IO){
            val quickJS = QuickJs.create()
            runCatching {
                quickJS.set("api",JavaScriptAPI::class.java, api)
                onResult.invoke(quickJS,quickJS.evaluate(javaScript))
                quickJS.close()
            }.onFailure { quickJS.close();onError.invoke(it) }
        }
    }
}