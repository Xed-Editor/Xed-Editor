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

object MenuItemHandler {
  fun update(activity: TabActivity) {
    activity.lifecycleScope.launch(Dispatchers.Default) {
      //wait until the menu is Initialized
      while (activity.isMenuInitialized().not()) {
        delay(50)
      }
      
      withContext(Dispatchers.Main) {
        val menu = activity.menu
        
        val show = activity.tabViewModel.fragmentFiles.isNotEmpty()
        editorMenu(menu, show)
        
        if (show) {
          activity.getCurrentFragment()?.file?.let {
            menu.findItem(R.id.run).isVisible = Runner.isRunnable(it)
          }
        } else {
          menu.findItem(R.id.run).isVisible = false
        }
        
        menu.findItem(R.id.git).isVisible = findGitRoot(activity.getCurrentFragment()?.file) != null
        
        updateUndoRedo(menu, activity.getCurrentFragment(), activity)
        
        searchMenu(menu, activity.getCurrentFragment()?.isSearching() ?: false)
        
        
      }
    }
  }
  
  
  private val set = HashSet<String>()
  private fun updateUndoRedo(menu: Menu, currentFragment: TabFragment?, activity: TabActivity) {
    
    menu.findItem(R.id.redo).isEnabled = currentFragment?.editor?.canRedo() == true
    menu.findItem(R.id.undo).isEnabled = currentFragment?.editor?.canUndo() == true
    
    if (currentFragment?.setListener?.not() == true) {
      currentFragment.let { it ->
        it.editor?.subscribeAlways(
          ContentChangeEvent::class.java
        ) {
          
          
          
          currentFragment.lifecycleScope.launch(Dispatchers.Default) {
            try {
              val fileName = currentFragment.file.name
              val index = activity.tabViewModel.fragmentFiles.indexOf(currentFragment.file)
              
              if (set.contains(fileName)) {
                // Check if the title doesn't already contain a '*'
                val currentTitle = activity.tabViewModel.fragmentTitles[index]
                if (!currentTitle.endsWith("*")) {
                  activity.tabViewModel.fragmentTitles[index] = "$currentTitle*"
                  
                  withContext(Dispatchers.Main) {
                    activity.tabLayout.getTabAt(index)?.text = activity.tabViewModel.fragmentTitles[index]
                  }
                }
              } else {
                set.add(fileName)
              }
            } catch (_: Exception) {}
          }
          
          
          
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