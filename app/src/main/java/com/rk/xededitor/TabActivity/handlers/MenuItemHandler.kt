package com.rk.xededitor.TabActivity.handlers

import android.view.Menu
import androidx.lifecycle.lifecycleScope
import com.rk.librunner.Runner
import com.rk.xededitor.R
import com.rk.xededitor.TabActivity.TabActivity
import com.rk.xededitor.TabActivity.editor.TabFragment
import com.rk.xededitor.TabActivity.file.FileManager.Companion.findGitRoot
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object MenuItemHandler {
    fun update(activity: TabActivity) {
        activity.lifecycleScope.launch(Dispatchers.Default) {
            //wait until the menu is Initialized
            while (activity.isMenuInitialized().not()) {
                delay(50)
            }

            withContext(Dispatchers.Main) {
                val menu = activity.menu

                val show = activity.fragmentFiles.isNotEmpty()
                editorMenu(menu,show)

                if(show){
                    activity.getCurrentFragment()?.file?.let {
                        menu.findItem(R.id.run).isVisible = Runner.isRunnable(it)
                    }
                }else{
                    menu.findItem(R.id.run).isVisible = false
                }

                menu.findItem(R.id.git).isVisible = findGitRoot(activity.getCurrentFragment()?.file) != null

                updateUndoRedo(menu, activity.getCurrentFragment())

                searchMenu(menu, activity.getCurrentFragment()?.isSearching() ?: false)


            }
        }
    }


    private fun updateUndoRedo(menu: Menu, currentFragment: TabFragment?) {

        menu.findItem(R.id.redo).isEnabled = currentFragment?.editor?.canRedo() == true
        menu.findItem(R.id.undo).isEnabled = currentFragment?.editor?.canUndo() == true

        if (currentFragment?.setListener?.not() == true) {
            currentFragment.let { it ->
                it.editor?.subscribeAlways(
                    ContentChangeEvent::class.java
                ) {
                    rkUtils.runOnUiThread {
                        menu.findItem(R.id.redo).isEnabled = it.editor.canRedo() == true
                        menu.findItem(R.id.undo).isEnabled = it.editor.canUndo() == true
                    }

                }

                it.setListener = true


            }
        }

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
            if (findItem(R.id.undo).isVisible){
                findItem(R.id.undo).isVisible = show.not()
            }
            if ( findItem(R.id.redo).isVisible){
                findItem(R.id.redo).isVisible = show.not()
            }
            if (findItem(R.id.action_save).isVisible){
                findItem(R.id.action_save).isVisible = show.not()
            }




        }
    }

}