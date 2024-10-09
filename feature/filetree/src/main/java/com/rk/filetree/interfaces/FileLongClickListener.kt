package com.rk.filetree.interfaces

import com.rk.filetree.model.Node

interface FileLongClickListener {
    fun onLongClick(node: Node<FileObject>)
}