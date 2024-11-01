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
import kotlinx.coroutines.withContext

object MenuItemHandler {
    fun update(activity: MainActivity) {
        // Launch a coroutine on the default dispatcher
        activity.lifecycleScope.launch(Dispatchers.Default) {
            // Check if the current fragment is an EditorFragment
            val currentFragment = activity.adapter.getCurrentFragment()?.fragment
            
            // Wait until the menu is initialized
            while (!activity.isMenuInitialized()) {
                delay(50)
            }
            
            // Switch to the main dispatcher for UI updates
            withContext(Dispatchers.Main) {
                val menu = activity.menu
                val hasFiles = activity.tabViewModel.fragmentFiles.isNotEmpty()
                
                val iseditor = if (currentFragment is EditorFragment) {
                    true
                } else {
                    false
                }
                // Show or hide editor-related menu items
                updateEditorMenuVisibility(menu, hasFiles && iseditor)
                
                // Show the "Run" option if there's a runnable file
                if (hasFiles && iseditor) {
                    (currentFragment as EditorFragment).file?.let { file ->
                        menu.findItem(Id.run).isVisible = Runner.isRunnable(file)
                    }
                } else {
                    menu.findItem(Id.run).isVisible = false
                }
                
                // Check for Git root and update Git menu item visibility
                updateGitMenuVisibility(
                    menu, if (iseditor) {
                        currentFragment as EditorFragment
                    } else {
                        null
                    }, activity
                )
                
                // Update undo/redo menu items and modified star
                updateUndoRedoAndModifiedStar(menu, activity.adapter.getCurrentFragment(), activity)
                
                // Toggle search-related menu items
                updateSearchMenu(
                    menu, if (iseditor) {
                        currentFragment as EditorFragment
                    } else {
                        null
                    }
                )
            }
        }
    }
    
    private suspend fun updateGitMenuVisibility(menu: Menu, editorFragment: EditorFragment?, activity: MainActivity) {
        // Determine the Git root on a background dispatcher
        if (editorFragment == null) {
            withContext(Dispatchers.Main) {
                menu.findItem(Id.git).isVisible = false
            }
        }
        withContext(Dispatchers.Default) {
            val gitRoot = editorFragment?.file?.let { findGitRoot(it) }
            
            // Update the Git menu item visibility on the main dispatcher
            withContext(Dispatchers.Main) {
                menu.findItem(Id.git).isVisible = gitRoot != null && activity.tabLayout.tabCount > 0
            }
        }
    }
    
    private fun updateUndoRedoAndModifiedStar(menu: Menu, currentFragment: TabFragment?, activity: MainActivity) {
        // Get the current fragment if it's an EditorFragment
        val editorFragment = activity.adapter.getCurrentFragment()?.fragment as? EditorFragment
        
        // Enable or disable undo/redo menu items based on the editor state
        menu.findItem(Id.redo).isEnabled = editorFragment?.editor?.canRedo() == true
        menu.findItem(Id.undo).isEnabled = editorFragment?.editor?.canUndo() == true
    }
    
    private fun updateEditorMenuVisibility(menu: Menu, show: Boolean) {
        // Toggle visibility of editor-related menu items
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
        // Toggle visibility of search-related menu items based on the search state
        menu.apply {
            findItem(Id.search_next).isVisible = isSearching
            findItem(Id.search_previous).isVisible = isSearching
            findItem(Id.search_close).isVisible = isSearching
            findItem(Id.replace).isVisible = isSearching
            
            // Hide editor-related menu items during search
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
