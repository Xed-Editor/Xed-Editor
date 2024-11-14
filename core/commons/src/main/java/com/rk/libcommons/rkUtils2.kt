package com.rk.libcommons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object rkUtils2 {
    suspend fun ui(action: suspend () -> Unit){
        withContext(Dispatchers.Main){
            action.invoke()
        }
    }
    
    suspend fun io(action: suspend () -> Unit){
        withContext(Dispatchers.IO){
            action.invoke()
        }
    }
}