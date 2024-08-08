package com.rk.librunner

import java.io.File

object Runner {
    fun isRunnable(file:File) : Boolean{
        return when(file.name.substringAfterLast('.', "")){
            "bsh" -> true
            "java" -> true
            "kt" -> true
            "js" -> true
            "html" -> true
            "sh" -> true
            "bash" -> true
            else -> false
        }
    }
    fun run(file: File?){

    }
}