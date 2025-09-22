package com.rk.tabs

//import com.rk.extension.Hooks
import com.rk.file.FileObject

object TabRegistry {

    suspend fun getTab(file: FileObject,callback: suspend (Tab?)-> Unit){
        when(file.getName().substringAfterLast(".").toString().trim()){
            "png","jpg","gif","jpeg" -> callback(ImageTab(file))


            else -> {
//                Hooks.Editor.tabs.values.forEach {
//                    if (it.shouldOpenForFile(file)){
//                        callback(it.tab)
//                        return
//                    }
//                }

                //open editor
                callback(null)
            }
        }
    }
}