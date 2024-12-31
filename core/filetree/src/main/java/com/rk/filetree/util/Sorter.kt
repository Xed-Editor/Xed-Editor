package com.rk.filetree.util

import com.rk.file.FileObject
import com.rk.filetree.model.Node

object Sorter {
    fun sort(root: com.rk.file.FileObject): List<Node<com.rk.file.FileObject>> {
        return root
            .listFiles()
            .sortedWith(compareBy<com.rk.file.FileObject> { file -> if (file.isFile()) 1 else 0 }.thenBy { it.getName().lowercase() })
            .map { Node(it) }
    }
}
