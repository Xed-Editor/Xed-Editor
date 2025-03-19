package com.rk.filetree.widget

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.filetree.adapters.FileTreeAdapter
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileIconProvider
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.model.Node
import com.rk.filetree.provider.DefaultFileIconProvider
import com.rk.filetree.provider.RecursiveFileObserver
import com.rk.filetree.util.sort
import com.rk.libcommons.PathUtils.toPath
import com.rk.xededitor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class FileTree : RecyclerView {
    private var fileTreeAdapter: FileTreeAdapter
    private lateinit var rootFileObject: FileObject
    private var watcher: RecursiveFileObserver? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    init {
        setItemViewCacheSize(70)
        layoutManager = LinearLayoutManager(context)
        val animation = AnimationUtils.loadLayoutAnimation(context, R.anim.animation)
        this.layoutAnimation = animation
        fileTreeAdapter = FileTreeAdapter(context, this)
    }

    fun getRootFileObject(): FileObject {
        return rootFileObject
    }

    fun setIconProvider(fileIconProvider: FileIconProvider) {
        fileTreeAdapter.iconProvider = fileIconProvider
    }

    fun setOnFileClickListener(clickListener: FileClickListener) {
        fileTreeAdapter.onClickListener = clickListener
    }

    fun setOnFileLongClickListener(longClickListener: FileLongClickListener) {
        fileTreeAdapter.onLongClickListener = longClickListener
    }

    private var init = false
    private var showRootNode: Boolean = true

    fun showRootNode(): Boolean {
        return showRootNode
    }

    suspend fun loadFiles(file: FileObject, showRootNodeX: Boolean? = null) =
        withContext(Dispatchers.IO) {
            watcher?.stopWatching()
            rootFileObject = file

            showRootNodeX?.let { showRootNode = it }

            val nodes: List<Node<FileObject>> =
                if (showRootNode) {
                    mutableListOf<Node<FileObject>>().apply { add(Node(file)) }
                } else {
                    sort(file)
                }

            if (init.not()) {
                if (fileTreeAdapter.iconProvider == null) {
                    fileTreeAdapter.iconProvider = DefaultFileIconProvider(context)
                }
                adapter = fileTreeAdapter
                init = true
            }
            fileTreeAdapter.submitList(nodes)
            if (showRootNode) {
                fileTreeAdapter.expandNode(nodes[0])
            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                if (file is FileWrapper) {
                    var path = file.getAbsolutePath()
                    watcher = RecursiveFileObserver(path = path, fileTree = this@FileTree)
                    watcher!!.startWatching()
                }
            }
        }

    suspend fun reloadFileChildren(parent: FileObject) {
        fileTreeAdapter.reloadFile(parent)
    }

    suspend fun onFileAdded(file: FileObject) {
        fileTreeAdapter.newFile(file)
    }

    fun onFileRemoved(file: FileObject) {
        fileTreeAdapter.removeFile(file)
    }

    fun onFileRenamed(file: FileObject, newFileObject: FileObject) {
        fileTreeAdapter.renameFile(file, newFileObject)
    }
}
