package com.rk.filetree.util

import com.rk.file_wrapper.FileObject
import com.rk.filetree.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun sort(root: FileObject): List<Node<FileObject>> = withContext(Dispatchers.IO) {
    root.listFiles()
        .sortedWith(compareBy<FileObject> { file -> if (file.isFile()) 1 else 0 }.thenBy {
            it.getName().lowercase()
        }).map { Node(it) }
}
