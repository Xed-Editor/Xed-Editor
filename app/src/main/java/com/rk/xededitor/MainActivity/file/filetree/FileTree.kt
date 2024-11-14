package com.rk.xededitor.MainActivity.file.filetree

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.DefaultScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.databinding.FiletreeLayoutBinding
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class FileTree(val context: Context, val path: String, val parent: ViewGroup) {
    val binding: FiletreeLayoutBinding
    private val fileListLoader = FileLoader()
    private var Ftree: Tree<File> = createTree(fileListLoader, path)
    
    init {
        // Inflate the layout
        val inflater = LayoutInflater.from(context)
        binding = FiletreeLayoutBinding.inflate(inflater, parent, true)
        
        // Initialize the file tree and set up TreeView
        setupTreeView()
    }
    
   
    
    private fun setupTreeView() {
        // Create and initialize the tree
        // Configure TreeView
        (binding.treeview as TreeView<File>).apply {
            binding.treeview.binder = FileBinder(
                binding = binding, fileLoader = fileListLoader, onFileLongClick = { file ->
                    
                    
                    
                    activityRef.get()?.apply {
                        projectManager.getSelectedProjectRootFile()?.let {
                            FileAction(this, it, file)
                        }
                    }
                
                
                }, onFileClick = { file ->
                    
                    activityRef.get()?.let {
                        if (it.isPaused) {
                            return@let
                        }
                        
                        it.adapter!!.addFragment(file)
                        if (
                            !PreferencesData.getBoolean(
                                PreferencesKeys.KEEP_DRAWER_LOCKED,
                                false,
                            )
                        ) {
                            it.binding!!.drawerLayout.close()
                        }
                        
                        DefaultScope.launch(Dispatchers.Main) {
                            delay(2000)
                            MenuItemHandler.update(it)
                        }
                    }
                
                    
                    
                }, context = context
            )
            binding.treeview.tree = Ftree
            setItemViewCacheSize(100)
            supportHorizontalScroll = true
            bindCoroutineScope(findViewTreeLifecycleOwner()?.lifecycleScope ?: return)
            
            nodeEventListener = binder as FileBinder
            selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
        }
        
        // Load file list and refresh TreeView
        DefaultScope.launch {
            fileListLoader.loadFiles(path)
            binding.treeview.refresh()
        }
        
    }
    
    private fun createTree(
        fileListLoader: FileLoader, rootPath: String
    ): Tree<File> {
        val tree = Tree.createTree<File>()
        tree.apply {
            this.generator = FileNodeFactory(
                File(rootPath), fileListLoader
            )
            initTree()
        }
        return tree
    }
}