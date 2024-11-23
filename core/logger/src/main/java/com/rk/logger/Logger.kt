package com.rk.logger

import android.util.Log

@Suppress("NOTHING_TO_INLINE")
class Logger(tag: Any?) {
    val tag:String = if (tag == null){
        "Logger-null"
    }else (if (tag is String){
        tag.toString()
    }else{
       "Logger-${tag::class.simpleName}"
    }).toString()
    
    inline fun log(log:String?){
        Log.d(tag,log ?: "null")
    }
    
    inline fun warn(log: String?){
        Log.w(tag,log ?: "null")
    }
    
    inline fun err(log: String?){
        Log.e(tag,log ?: "null")
    }
    
    inline fun error(log: String?){
        err(log)
    }
    
    //-----------------------------
    
    inline fun log(log:Any?){
        log(log.toString())
    }
    
    inline fun warn(log: Any?){
        warn(log.toString())
    }
    
    inline fun err(log: Any?){
        err(log.toString())
    }
    
    inline fun error(log: Any?){
        error(log.toString())
    }
    
    companion object{
        @JvmStatic
        val instance = Logger("StaticLogger")
    }
    
}
