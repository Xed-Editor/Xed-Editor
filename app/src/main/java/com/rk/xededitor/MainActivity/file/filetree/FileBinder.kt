package com.rk.xededitor.MainActivity.file.filetree

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import com.rk.xededitor.DefaultScope
import com.rk.xededitor.MainActivity.file.filetree.events.FileTreeEvents
import com.rk.xededitor.R
import com.rk.xededitor.databinding.FiletreeDirBinding
import com.rk.xededitor.databinding.FiletreeFileBinding
import com.rk.xededitor.databinding.FiletreeLayoutBinding
import com.rk.xededitor.rkUtils
import io.github.dingyi222666.view.treeview.TreeNode
import io.github.dingyi222666.view.treeview.TreeNodeEventListener
import io.github.dingyi222666.view.treeview.TreeView
import io.github.dingyi222666.view.treeview.TreeViewBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

class FileBinder(
    private val binding: FiletreeLayoutBinding,
    private val context: Context,
    private val fileLoader: FileLoader,
    private val onFileLongClick: (File) -> Unit = {},
    private val onFileClick: (File) -> Unit
) : TreeViewBinder<File>(), TreeNodeEventListener<File> {
    
    private val TAG="FIleBinder"
    
    override fun createView(parent: ViewGroup, viewType: Int): View {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            FiletreeDirBinding.inflate(layoutInflater, parent, false).root
        } else {
            FiletreeFileBinding.inflate(layoutInflater, parent, false).root
        }
    }
    
    override fun getItemViewType(node: TreeNode<File>): Int = if (node.isChild) {
        1
    } else {
        0
    }
    
    override fun bindView(holder: TreeView.ViewHolder, node: TreeNode<File>, listener: TreeNodeEventListener<File>) {
        if (node.isChild) {
            applyDir(holder, node)
        } else {
            applyFile(holder, node)
        }
        val itemView = holder.itemView.findViewById<Space>(R.id.space)
        itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = node.depth * rkUtils.dpToPx(22f, context)
        }
    }
    
    private val icons = mutableMapOf<String,Drawable>()
    private fun applyFile(holder: TreeView.ViewHolder, node: TreeNode<File>) {
        val start = System.currentTimeMillis()
        val binding = FiletreeFileBinding.bind(holder.itemView)
        val ext = node.requireData().name.substringAfterLast('.', "")
        
        val icon = icons[ext] ?: AppCompatResources.getDrawable(
            binding.root.context, getIcon(node.requireData())
        )!!.also { icons[ext] = it }
        
        icon.setBounds(0, 0, rkUtils.dpToPx(16f, context), rkUtils.dpToPx(16f, context))
        
        binding.tvName.apply {
            text = node.name.toString()
            setCompoundDrawables(
                icon, null, null, null
            )
        }
    }
    
    private var folderDrawable:Drawable? = null
    private fun applyDir(holder: TreeView.ViewHolder, node: TreeNode<File>) {
        val binding = FiletreeDirBinding.bind(holder.itemView)
        
        val icon = folderDrawable ?: AppCompatResources.getDrawable(
            binding.root.context, com.rk.libcommons.R.drawable.folder
        )!!.also { folderDrawable = it }
        
        icon.setBounds(0, 0, rkUtils.dpToPx(16f, context), rkUtils.dpToPx(16f, context))
        binding.tvName.text = node.name.toString()
        binding.tvName.setCompoundDrawables(
            icon, null, null, null
        )
        binding.ivArrow.animate().rotation(if (node.expand) 90f else 0f).setDuration(200).start()
        
        val path = node.requireData().absolutePath
        if (fileLoader.getLoadedFiles(path).isEmpty()){
            DefaultScope.launch(Dispatchers.IO) {
                fileLoader.loadFiles(path, maxLayers = 1)
            }
        }
    }
    
    override fun onClick(node: TreeNode<File>, holder: TreeView.ViewHolder) {
        if (node.isChild) {
            applyDir(holder, node)
        } else {
            onFileClick(node.requireData())
        }
    }
    override fun onLongClick(node: TreeNode<File>, holder: TreeView.ViewHolder): Boolean {
        onFileLongClick(node.requireData())
        return true
    }
    
    override fun onRefresh(status: Boolean) {
        DefaultScope.launch(Dispatchers.Main){
            binding.progress.isInvisible = !status
        }
    }
    
    override fun onToggle(
        node: TreeNode<File>,
        isExpand: Boolean,
        holder: TreeView.ViewHolder
    ) {
        applyDir(holder, node)
    }
    
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onRefreshFolderEvent(event: FileTreeEvents.OnRefreshFolderEvent) {
        CoroutineScope(Dispatchers.Main).launch {
            fileLoader.removeLoadedFile(event.openedFolder)
            binding.treeview.refresh()
        }
    }
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onDeleteFileEvent(event: FileTreeEvents.OnDeleteFileEvent) {
        CoroutineScope(Dispatchers.Main).launch {
            fileLoader.removeLoadedFile(event.file)
            binding.treeview.refresh()
        }
    }
    
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onCreateFileEvent(event: FileTreeEvents.OnCreateFileEvent) {
        CoroutineScope(Dispatchers.Main).launch {
            fileLoader.createLoadedFile(event.file)
            binding.treeview.refresh()
        }
    }
    
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onRenameFileEvent(event: FileTreeEvents.OnRenameFileEvent){
        CoroutineScope(Dispatchers.Main).launch {
            fileLoader.renameLoadedFile(event.oldFile,event.newFile)
            binding.treeview.refresh()
        }
    }
    
    
    
}