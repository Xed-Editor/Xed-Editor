package com.rk.wasm3

import android.widget.Toast
import com.rk.libcommons.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


object Wasm3Api {
    
    @JvmStatic
    fun toast(s: String?) {
        GlobalScope.launch(Dispatchers.Main){
            Toast.makeText(application, s, Toast.LENGTH_SHORT).show()
        }
        
    }
}
