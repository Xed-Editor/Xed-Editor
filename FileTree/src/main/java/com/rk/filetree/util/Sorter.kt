package com.rk.filetree.util

import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node

object Sorter {
    fun sort(root: FileObject): List<Node<FileObject>> {
        return root.listFiles()
            .sortedWith(compareBy<FileObject> { !it.isDirectory() }.thenBy { it.getName() }).map { Node(it) }
    }

}