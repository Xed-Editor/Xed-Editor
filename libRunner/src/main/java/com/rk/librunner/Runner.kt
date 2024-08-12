package com.rk.librunner

import android.content.Context
import android.widget.Toast
import com.rk.librunner.beanshell.BeanshellRunner
import com.rk.librunner.beanshell.PluginServerRunner
import java.io.File

object Runner {

    private val registry = HashMap<String,List<RunnableInterface>>()

    init {
        registry["bsh"] = arrayListOf(BeanshellRunner())
    }

    fun isRunnable(file:File) : Boolean{
        val ext = file.name.substringAfterLast('.', "")
        return registry.keys.any { it == ext }
    }

    fun run(file: File,context:Context){
        if (isRunnable(file)){
            val ext = file.name.substringAfterLast('.', "")
            val runners = registry[ext]
            if (runners?.size == 1){
                runners[0].run(file,context)
            }else{
                //show a popup

            }
        }
    }
}