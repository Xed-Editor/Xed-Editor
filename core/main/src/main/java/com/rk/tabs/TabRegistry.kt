package com.rk.tabs

import com.rk.file.FileObject

object TabRegistry {

    suspend fun getTab(file: FileObject,callback: suspend (Tab?)-> Unit){
        when(file.getName().substringAfterLast(".").toString().trim()){
            "png","jpg","gif","jpeg" -> callback(ImageTab(file))
        }
    }
}