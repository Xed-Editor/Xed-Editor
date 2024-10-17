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
                        menu.findItem(R.id.run).isVisible =
                            it.file?.let { it1 -> Runner.isRunnable(it1) } == true
                    }
                } else {
                    menu.findItem(R.id.run).isVisible = false
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
                        menu.findItem(R.id.git).isVisible =
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
        
        menu.findItem(R.id.redo).isEnabled = editorFragment?.editor?.canRedo() == true
        menu.findItem(R.id.undo).isEnabled = editorFragment?.editor?.canUndo() == true
    }

    private fun editorMenu(menu: Menu, show: Boolean) {
        with(menu) {
            findItem(R.id.action_save).isVisible = show
            findItem(R.id.action_all).isVisible = show
            findItem(R.id.action_print).isVisible = show
            findItem(R.id.batchrep).isVisible = show
            findItem(R.id.search).isVisible = show
            findItem(R.id.share).isVisible = show
            findItem(R.id.undo).isVisible = show
            findItem(R.id.redo).isVisible = show
            findItem(R.id.suggestions).isVisible = show
        }
    }

    private fun searchMenu(menu: Menu, show: Boolean) {
        with(menu) {
            findItem(R.id.search_next).isVisible = show
            findItem(R.id.search_previous).isVisible = show
            findItem(R.id.search_close).isVisible = show
            findItem(R.id.replace).isVisible = show

            if (findItem(R.id.run).isVisible) {
                findItem(R.id.run).isVisible = show.not()
            }
            if (findItem(R.id.undo).isVisible) {
                findItem(R.id.undo).isVisible = show.not()
            }
            if (findItem(R.id.redo).isVisible) {
                findItem(R.id.redo).isVisible = show.not()
            }
            if (findItem(R.id.action_save).isVisible) {
                findItem(R.id.action_save).isVisible = show.not()
            }
        }
    }
}
