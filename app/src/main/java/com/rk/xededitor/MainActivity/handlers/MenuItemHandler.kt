package com.rk.xededitor.MainActivity.handlers

import android.view.Menu
import androidx.lifecycle.lifecycleScope
import com.rk.runner.Runner
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.file.FileManager.Companion.findGitRoot
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext



object MenuItemHandler {
    
    private val mutex = Mutex()
    private var lastUpdate = System.currentTimeMillis()
    private var isUpdating = false
    
    fun update(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.Default) {
            
            //prevent lag
            mutex.withLock {
                if (isUpdating || System.currentTimeMillis() - lastUpdate < 1500) {
                    return@withLock
                }
                isUpdating = true
                lastUpdate = System.currentTimeMillis()
            }
            
            //wait for menu
            while (!activity.isMenuInitialized()) {delay(100)}
            
            updateInternal(activity)
            
            mutex.withLock {isUpdating = false}
            
        }
        
        
    }
    
    private suspend fun updateInternal(activity: MainActivity){
        withContext(Dispatchers.Main) {
            val currentFragment = activity.adapter!!.getCurrentFragment()?.fragment
            val menu = activity.menu
            val hasFiles = activity.tabViewModel.fragmentFiles.isNotEmpty()
            val isEditor = currentFragment is EditorFragment
            
            updateEditorMenuVisibility(menu!!, hasFiles && isEditor)
            
            if (hasFiles && isEditor) {
                (currentFragment as EditorFragment).file?.let { file ->
                    menu.findItem(Id.run).isVisible = Runner.isRunnable(file)
                }
            } else {
                menu.findItem(Id.run).isVisible = false
            }
            
            updateGitMenuVisibility(
                menu, if (isEditor) {
                    currentFragment as EditorFragment
                } else {
                    null
                }, activity
            )
            
            if (isEditor){
                activity.adapter!!.getCurrentFragment()?.let { updateUndoRedoAndModifiedStar(menu, it, activity) }
            }
            
            updateSearchMenu(
                menu, if (isEditor) {
                    currentFragment as EditorFragment
                } else {
                    null
                }
            )
        }
    }
    
    private suspend fun updateGitMenuVisibility(menu: Menu, editorFragment: EditorFragment?, activity: MainActivity) {
        if (editorFragment == null) {
            withContext(Dispatchers.Main) {
                menu.findItem(Id.git).isVisible = false
            }
        }
        withContext(Dispatchers.Default) {
            val gitRoot = editorFragment?.file?.let { findGitRoot(it) }
            
            withContext(Dispatchers.Main) {
                menu.findItem(Id.git).isVisible = gitRoot != null && activity.tabLayout!!.tabCount > 0
            }
        }
    }
    
    private fun updateUndoRedoAndModifiedStar(menu: Menu, currentFragment: TabFragment, activity: MainActivity) {
        val editorFragment = currentFragment.fragment as EditorFragment
        menu.findItem(Id.redo).isEnabled = editorFragment.editor?.canRedo() == true
        menu.findItem(Id.undo).isEnabled = editorFragment.editor?.canUndo() == true
    }
    
    private fun updateEditorMenuVisibility(menu: Menu, show: Boolean) {
        menu.apply {
            findItem(Id.action_save).isVisible = show
            findItem(Id.action_all).isVisible = show
            findItem(Id.action_print).isVisible = show
            findItem(Id.batchrep).isVisible = show
            findItem(Id.search).isVisible = show
            findItem(Id.share).isVisible = show
            findItem(Id.undo).isVisible = show
            findItem(Id.redo).isVisible = show
            findItem(Id.suggestions).isVisible = show
        }
    }
    
    private fun updateSearchMenu(menu: Menu, editorFragment: EditorFragment?) {
        val isSearching = editorFragment != null && (editorFragment.editor?.isSearching() == true)
        menu.apply {
            findItem(Id.search_next).isVisible = isSearching
            findItem(Id.search_previous).isVisible = isSearching
            findItem(Id.search_close).isVisible = isSearching
            findItem(Id.replace).isVisible = isSearching
            
            if (findItem(Id.run).isVisible) {
                findItem(Id.run).isVisible = !isSearching
            }
            if (findItem(Id.undo).isVisible) {
                findItem(Id.undo).isVisible = !isSearching
            }
            if (findItem(Id.redo).isVisible) {
                findItem(Id.redo).isVisible = !isSearching
            }
            if (findItem(Id.action_save).isVisible) {
                findItem(Id.action_save).isVisible = !isSearching
            }
        }
    }
}
