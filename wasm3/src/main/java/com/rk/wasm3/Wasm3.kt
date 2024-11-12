package com.rk.wasm3

class Wasm3 {
    companion object{
        private external fun runMain()
        init {
            System.loadLibrary("wasm3")
            
            //logger
            runMain()
        }
    }
    
    private val envField: Long = 0
    private val runtimeField: Long = 0
    
    
    external fun initializeWasmEnvironment(filePath: String?): Int
    external fun callFunctionInWasm(funcName: String?): Int
    external fun cleanupWasm()
    
}