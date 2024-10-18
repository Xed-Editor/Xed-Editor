package com.rk.xededitor.MainActivity.handlers

import android.view.Menu
import androidx.lifecycle.lifecycleScope
import com.rk.runner.Runner
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.TabFragment
import com.rk.xededitor.MainActivity.editor.fragments.editor.EditorFragment
import com.rk.xededitor.MainActivity.file.FileManager.Companion.findGitRoot
import com.rk.xededitor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object MenuItemHandler {
    fun update(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.Default) {
            val editorFragment = if (activity.adapter.getCurrentFragment()?.fragment is EditorFragment){
                activity.adapter.getCurrentFragment()?.fragment as EditorFragment
            }else{
                null
            }
            // wait until the menu is Initialized
            while (activity.isMenuInitialized().not()) {
                delay(50)
            }

            withContext(Dispatchers.Main) {
                val menu = activity.menu

                val show = activity.tabViewModel.fragmentFiles.isNotEmpty()
                editorMenu(menu, show)

                if (show) {
                    editorFragment?.let {
                        menu.findItem(Id.run).isVisible =
                            it.file?.let { it1 -> Runner.isRunnable(it1) } == true
                    }
                } else {
                    menu.findItem(Id.run).isVisible = false
                }
                withContext(Dispatchers.Default) {
                    val xc = editorFragment?.file
                    val gitRoot =
                        if (xc == null) {
                            null
                        } else {
                            findGitRoot(editorFragment.file)
                        }

                    withContext(Dispatchers.Main) {
                        menu.findItem(Id.git).isVisible =
                            xc != null && gitRoot != null && activity.tabLayout.tabCount > 0
                    }
                }
                
                updateUndoRedoAndModifiedStar(menu, activity.adapter.getCurrentFragment(), activity)

                searchMenu(menu, editorFragment?.editor?.isSearching() ?: false)
            }
        }
    }

    

    private fun updateUndoRedoAndModifiedStar(menu: Menu, currentFragment: TabFragment?, activity: MainActivity) {
        val editorFragment = if (activity.adapter.getCurrentFragment()?.fragment is EditorFragment){
            activity.adapter.getCurrentFragment()?.fragment as EditorFragment
        }else{
            null
        }
        
        menu.findItem(Id.redo).isEnabled = editorFragment?.editor?.canRedo() == true
        menu.findItem(Id.undo).isEnabled = editorFragment?.editor?.canUndo() == true
    }

    private fun editorMenu(menu: Menu, show: Boolean) {
        with(menu) {
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

    private fun searchMenu(menu: Menu, show: Boolean) {
        with(menu) {
            findItem(Id.search_next).isVisible = show
            findItem(Id.search_previous).isVisible = show
            findItem(Id.search_close).isVisible = show
            findItem(Id.replace).isVisible = show

            if (findItem(Id.run).isVisible) {
                findItem(Id.run).isVisible = show.not()
            }
            if (findItem(Id.undo).isVisible) {
                findItem(Id.undo).isVisible = show.not()
            }
            if (findItem(Id.redo).isVisible) {
                findItem(Id.redo).isVisible = show.not()
            }
            if (findItem(Id.action_save).isVisible) {
                findItem(Id.action_save).isVisible = show.not()
            }
        }
    }
}
