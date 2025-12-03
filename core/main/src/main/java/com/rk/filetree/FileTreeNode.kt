package com.rk.filetree

import com.rk.file.FileObject

data class FileTreeNode(val file: FileObject, val isFile: Boolean, val isDirectory: Boolean, val name: String)
