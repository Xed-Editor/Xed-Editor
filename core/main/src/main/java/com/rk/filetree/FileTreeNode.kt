package com.rk.filetree

import com.rk.file.FileObject

data class FileTreeNode(val file: FileObject, val isFile: Boolean, val isExpandable: Boolean, val name: String)
