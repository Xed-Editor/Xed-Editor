package com.rk.wasm3

class Wasm3 {
    companion object {
        private external fun runMain()
        init {
            System.loadLibrary("wasm3")
            
            //redirect stdout and stderr to android logcat
            runMain()
            
            
        }
    }
    
    external fun loadWasm(path: String, vararg functionName: String)
    
}