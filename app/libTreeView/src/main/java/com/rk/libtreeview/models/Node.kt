package com.rk.libtreeview.models

import com.rk.libtreeview.interfaces.FileObject
import java.io.File

data class Node<T>(
    var value: T,
    var parent: Node<T>? = null,
    var child: List<Node<T>>? = null,
    var isExpand: Boolean = false,
    var level: Int = 0,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node<*>

        return value == other.value && parent == other.parent && child == other.child && isExpand == other.isExpand && level == other.level
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
