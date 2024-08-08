package com.rk.libtreeview.callback

import androidx.recyclerview.widget.DiffUtil
import com.rk.libtreeview.interfaces.FileObject
import com.rk.libtreeview.models.Node

class NodeDiffCallBack : DiffUtil.ItemCallback<Node<FileObject>>() {
    override fun areItemsTheSame(
        oldItem: Node<FileObject>, newItem: Node<FileObject>
    ): Boolean {
        return oldItem.value.absolutePath == newItem.value.absolutePath
    }

    override fun areContentsTheSame(
        oldItem: Node<FileObject>, newItem: Node<FileObject>
    ): Boolean {
        return oldItem == newItem
    }
}