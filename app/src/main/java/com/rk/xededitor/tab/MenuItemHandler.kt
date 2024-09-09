package com.rk.xededitor.tab

import android.view.Menu
import androidx.lifecycle.lifecycleScope
import com.rk.librunner.Runner
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MenuItemHandler {

    fun update(activity: TabActivity) {
        activity.lifecycleScope.launch(Dispatchers.Default) {
            //wait until the menu is Initialized
            while (activity.isMenuInitialized().not()) {
                delay(50)
            }

            withContext(Dispatchers.Main) {
                val menu = activity.menu
                editorMenu(menu, activity.fragmentFiles.isNotEmpty())
                searchMenu(menu, activity.getCurrentFragment()?.get()?.isSearching() ?: false)
                activity.getCurrentFragment()?.get()?.file?.let {
                    menu.findItem(R.id.run).isVisible =
                        activity.fragmentFiles.isNotEmpty() && Runner.isRunnable(it)
                }
                updateUndoRedo(menu,activity.getCurrentFragment()?.get())

            }
        }
    }

    private var setListener = false
    private fun updateUndoRedo(menu: Menu,currentFragment: TabFragment?){
        if (setListener.not()){
            currentFragment?.let { it ->
                it.editor?.subscribeAlways(
                    ContentChangeEvent::class.java
                ) {
                    rkUtils.runOnUiThread{
                        menu.findItem(R.id.redo).isEnabled = it.editor.canRedo() == true
                        menu.findItem(R.id.undo).isEnabled = it.editor.canUndo() == true
                    }

                }


            }

            setListener = true
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
        }
    }

}