package com.rk.libtreeview

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.rk.libtreeview.adapters.TreeViewAdapter
import com.rk.libtreeview.interfaces.FileObject
import com.rk.libtreeview.interfaces.onClickListener
import com.rk.libtreeview.models.Node

class TreeView : RecyclerView {
    private var context: Context
    private lateinit var adapter: TreeViewAdapter
    constructor(context: Context) : super(context) { this.context = context }
    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet) { this.context = context }
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) { this.context = context }

    companion object {
        var opened_file_path = ""
    }

    init {
        setItemViewCacheSize(200)
    }

    fun setContext(context: Context){
        this.context = context
    }

    fun init(root: FileObject){
        opened_file_path = root.absolutePath.toString()
        this.adapter = TreeViewAdapter(context,root)
    }

    fun setFileClickListener(listener:onClickListener){
        adapter.setListener(listener)
    }

    fun onDelete(parent: Node<FileObject>,node: Node<FileObject>){

    }

    fun onCreate(parent: Node<FileObject>,node: Node<FileObject>){

    }

    fun update(parent: Node<FileObject>,node: Node<FileObject>){

    }

    fun refresh(){

    }

}
