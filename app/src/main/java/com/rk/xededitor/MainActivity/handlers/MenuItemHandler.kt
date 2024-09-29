package com.rk.xededitor.MainActivity.handlers

import android.view.Menu
import androidx.lifecycle.lifecycleScope
import com.rk.librunner.Runner
import com.rk.xededitor.R
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.TabFragment
import com.rk.xededitor.MainActivity.file.FileManager.Companion.findGitRoot
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MenuItemHandler {
  fun update(activity: MainActivity) {
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
          activity.adapter.getCurrentFragment()?.let {
            menu.findItem(R.id.run).isVisible = it.file?.let { it1 -> Runner.isRunnable(it1) } == true
          }
        } else {
          menu.findItem(R.id.run).isVisible = false
        }
        withContext(Dispatchers.Default){
          val xc = activity.adapter.getCurrentFragment()?.file
          val gitRoot = if(xc == null){
            null
          }else{
            findGitRoot(activity.adapter.getCurrentFragment()?.file)
          }

          withContext(Dispatchers.Main){
            menu.findItem(R.id.git).isVisible = xc != null && gitRoot != null && activity.tabLayout.tabCount > 0
          }

        }

        updateUndoRedo(menu, activity.adapter.getCurrentFragment(), activity)
        
        searchMenu(menu, activity.adapter.getCurrentFragment()?.isSearching() ?: false)

        
      }
    }
  }
  
  
  val set = HashSet<String>()
  private fun updateUndoRedo(menu: Menu, currentFragment: TabFragment?, activity: MainActivity) {
    
    menu.findItem(R.id.redo).isEnabled = currentFragment?.editor?.canRedo() == true
    menu.findItem(R.id.undo).isEnabled = currentFragment?.editor?.canUndo() == true
    
    if (currentFragment?.setListener?.not() == true) {
      currentFragment.let {
        it.editor?.subscribeAlways(
          ContentChangeEvent::class.java
        ) {
          menu.findItem(R.id.redo).isEnabled = currentFragment.editor?.canRedo() == true
          menu.findItem(R.id.undo).isEnabled = currentFragment.editor?.canUndo() == true
          
          currentFragment.lifecycleScope.launch(Dispatchers.Default) {
            try {
              val fileName = currentFragment.file!!.name
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
          
        }
        if (it.setListener.not()){
          it.setListener = true
        }
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