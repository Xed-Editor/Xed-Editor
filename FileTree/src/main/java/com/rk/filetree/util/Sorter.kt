package com.rk.filetree.util

import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node

object Sorter {
    fun sort(root: FileObject) : List<Node<FileObject>>{
        val list = (root.listFiles() ?: return emptyList()).toMutableList()
        val dirs = list.filter { it.isDirectory() }.sortedBy { it.getName() }
        val files = (list - dirs.toSet()).sortedBy { it.getName() }
        return (dirs + files).map { Node(it) }.toMutableList()
    }
}