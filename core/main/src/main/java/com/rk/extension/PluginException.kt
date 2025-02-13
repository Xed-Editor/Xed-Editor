package com.rk.extension

class PluginException(msg: String?){
    private var cause:Throwable? = null
    constructor(msg: String?,cause:Throwable) : this(msg){
        this.cause = cause
    }

    constructor(extension: Extension,msg: String?) : this(msg){

    }

    constructor(extension: Extension,msg: String?,cause: Throwable) : this(msg){
        println("PluginError : $extension \n $msg \n ${cause.message}")
    }

    init {

    }
}