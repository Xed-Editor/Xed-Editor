package com.rk.filetree.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.filetree.adapters.FileTreeAdapter
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileIconProvider
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.filetree.provider.DefaultFileIconProvider

class FileTree : RecyclerView {
    private var fileTreeAdapter: FileTreeAdapter
    private lateinit var rootFileObject: FileObject

    constructor(context:Context) : super(context)
    constructor(context:Context,attrs:AttributeSet) : super(context,attrs)
    constructor(context:Context,attrs:AttributeSet,defStyleAttr:Int) : super(context,attrs, defStyleAttr)

    init {
        setItemViewCacheSize(100)
        layoutManager = LinearLayoutManager(context)
        fileTreeAdapter = FileTreeAdapter(context,this)
    }

    fun setIconProvider(fileIconProvider: FileIconProvider){
        fileTreeAdapter.iconProvider = fileIconProvider
    }

    fun setOnFileClickListener(clickListener: FileClickListener){
        fileTreeAdapter.onClickListener = clickListener
    }

    fun setOnFileLongClickListener(longClickListener: FileLongClickListener){
        fileTreeAdapter.onLongClickListener = longClickListener
    }


    private var init = false
    fun loadFiles(file: FileObject){
        rootFileObject = file
        val nodes = mutableListOf<Node<FileObject>>()
        if (init.not()){
            nodes.add(Node(file))
            if (fileTreeAdapter.iconProvider == null){
                fileTreeAdapter.iconProvider = DefaultFileIconProvider(context)
            }
            adapter = fileTreeAdapter
            fileTreeAdapter.submitList(nodes)
            init = true
        }else{
            nodes.add(Node(file))
            fileTreeAdapter.submitList(nodes)
        }
    }


    fun reloadFileTree(){
        val nodes = mutableListOf<Node<FileObject>>()
        nodes.add(Node(rootFileObject))
        fileTreeAdapter.submitList(nodes)
    }

}