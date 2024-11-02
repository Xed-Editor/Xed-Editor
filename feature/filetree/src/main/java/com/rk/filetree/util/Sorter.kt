package com.rk.filetree.util

import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node

object Sorter {
    fun sort(root: FileObject): List<Node<FileObject>> {
        return root
            .listFiles()
            .sortedWith(compareBy<FileObject> { file -> if (file.isFile()) 1 else 0 }.thenBy { it.getName().lowercase() })
            .map { Node(it) }
    }
}
