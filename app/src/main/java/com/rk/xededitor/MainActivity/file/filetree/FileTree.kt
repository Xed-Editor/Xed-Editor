package com.rk.xededitor.MainActivity.file.filetree

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.DefaultScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.databinding.FiletreeLayoutBinding
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.rk.xededitor.MainActivity.file.filesystem.SFTPFilesystem
import com.rk.libcommons.LoadingPopup

import com.rk.xededitor.rkUtils

class FileTree(val context: MainActivity, val path: String, val parent: ViewGroup) {
    val binding: FiletreeLayoutBinding
    
    private val viewModel: FileTreeViewModel by lazy {
        ViewModelProvider(context)[FileTreeViewModel::class.java]
    }
    
    class FileTreeViewModel() : ViewModel() {
        val fileListLoader: FileLoader = FileLoader()
        private val treeMap = HashMap<String,Tree<File>>()
        
        fun getTree(path: String):Tree<File>?{
            return treeMap[path]
        }
        
        fun setTree(path: String,tree:Tree<File>){
            treeMap[path] = tree
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
    
    init {
        val inflater = LayoutInflater.from(context)
        binding = FiletreeLayoutBinding.inflate(inflater, parent, true)
        
        if (viewModel.getTree(path) == null) {
            viewModel.setTree(path,createTree(viewModel.fileListLoader, path))
        }
        
        setupTreeView()
    }
    
    
    private fun setupTreeView() {
        (binding.treeview as TreeView<File>).apply {
            binding.treeview.binder = FileBinder(binding = binding, fileLoader = viewModel.fileListLoader, onFileLongClick = { file ->
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
                    val config = SFTPFilesystem.getConfig(file.absoulutePath, 1)
                    rkUtils.toast(config)
                    if (config != "") {
                        val loading = LoadingPopup(it, null)
                        DefaultScope.launch(Dispatchers.Main) {
                            loading.show()
                            withContext(Dispatchers.IO) {
                                it.projectManager.sftpProjects[config]!!.load(SFTPFilesystem.getConfig(file.absolutePath, 2))
                            }
                            loading.hide()
                            it.adapter!!.addFragment(file)
                            if (!PreferencesData.getBoolean(
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
                    } else {
                        it.adapter!!.addFragment(file)
                        if (!PreferencesData.getBoolean(
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
                }
            }, context = context
            )
            
            binding.treeview.tree = viewModel.getTree(path)!!
            setItemViewCacheSize(100)
            supportHorizontalScroll = true
            bindCoroutineScope(findViewTreeLifecycleOwner()?.lifecycleScope ?: return)
            
            nodeEventListener = binder as FileBinder
            selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
        }
        
        DefaultScope.launch {
            viewModel.fileListLoader.loadFiles(path)
            binding.treeview.refresh()
        }
        
    }
    
    
}
