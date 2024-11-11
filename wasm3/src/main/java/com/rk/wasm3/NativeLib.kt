package com.rk.wasm3

class NativeLib {
    
    /**
     * A native method that is implemented by the 'wasm3' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
    
    companion object {
        // Used to load the 'wasm3' library on application startup.
        init {
            System.loadLibrary("wasm3")
        }
    }
}