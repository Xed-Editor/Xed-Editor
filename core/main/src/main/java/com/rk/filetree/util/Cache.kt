package com.rk.filetree.util

import com.rk.file_wrapper.FileObject
import com.rk.filetree.model.Node
import java.util.WeakHashMap

object Cache {
    private val map = WeakHashMap<FileObject, List<Node<FileObject>>>()

    fun getFiles(parent:FileObject): List<Node<FileObject>>?{
        return synchronized(map){
            map[parent]
        }
    }

    fun setFiles(parent: FileObject,files: List<Node<FileObject>>){
        synchronized(map){
            map[parent] = files
        }
    }
}