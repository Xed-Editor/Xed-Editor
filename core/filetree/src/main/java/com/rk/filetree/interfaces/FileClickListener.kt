package com.rk.filetree.interfaces

import com.rk.filetree.model.Node

interface FileClickListener {
    fun onClick(node: Node<com.rk.file.FileObject>)
}
