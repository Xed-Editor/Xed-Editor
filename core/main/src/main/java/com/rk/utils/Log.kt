package com.rk.utils

import android.util.Log


fun Any.debug(msg:String){
    Log.d(this::class.java.simpleName,msg)
}

fun Any.error(msg:String){
    Log.e(this::class.java.simpleName,msg)
}

fun Any.info(msg:String){
    Log.i(this::class.java.simpleName,msg)
}
