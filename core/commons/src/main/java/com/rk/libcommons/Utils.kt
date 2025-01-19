package com.rk.libcommons

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(DelicateCoroutinesApi::class)
fun postIO(block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(Dispatchers.IO, block = block)
}

suspend fun IO(block: suspend CoroutineScope.() -> Unit){
    withContext(Dispatchers.IO,block)
}

suspend fun Default(block: suspend CoroutineScope.() -> Unit){
    withContext(Dispatchers.Default,block)
}

suspend fun UI(block: suspend CoroutineScope.() -> Unit){
    withContext(Dispatchers.Main,block)
}

inline fun runOnUiThread(runnable: Runnable) {
    Handler(Looper.getMainLooper()).post(runnable)
}

inline fun toast(message: String?) {
    runOnUiThread { Toast.makeText(application!!, message.toString(), Toast.LENGTH_SHORT).show() }
}

inline fun String?.toastIt(){
    toast(this)
}

inline fun dpToPx(dp: Float, ctx: Context): Int {
    val density = ctx.resources.displayMetrics.density
    return Math.round(dp * density)
}
