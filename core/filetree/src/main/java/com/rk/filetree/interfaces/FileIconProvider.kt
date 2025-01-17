package com.rk.filetree.interfaces

import android.graphics.drawable.Drawable
import com.rk.filetree.model.Node

interface FileIconProvider {
    fun getIcon(node: Node<com.rk.file.FileObject>): Drawable?
    fun getChevronRight(): Drawable?
}
