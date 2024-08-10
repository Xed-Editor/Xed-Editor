package com.rk.librunner

import android.content.Context
import android.widget.Toast
import java.io.File

object Runner {
    fun isRunnable(file:File) : Boolean{
        return when(file.name.substringAfterLast('.', "")){
            "bsh" -> true
            "js" -> true
            "html" -> true
            "sh" -> true
            "bash" -> true
            else -> false
        }
    }
    fun run(file: File?,context:Context){
        Toast.makeText(context,"this feature is not implemented",Toast.LENGTH_SHORT).show()
    }
}