package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.MainActivity.editor.TabFragment
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.WeakHashMap

private var nextItemId = 0L
val tabFragments = WeakHashMap<Int, TabFragment>()
const val tabLimit = 20

class TabAdapter(private val mainActivity: MainActivity) :
  FragmentStateAdapter(mainActivity.supportFragmentManager, mainActivity.lifecycle) {
  
  
  fun getCurrentFragment(): TabFragment? {
    return tabFragments[mainActivity.tabLayout.selectedTabPosition]
  }
  
  private val itemIds = mutableMapOf<Int, Long>()
  
  override fun getItemCount(): Int = mainActivity.tabViewModel.fragmentFiles.size
  
  override fun createFragment(position: Int): Fragment {
    val file = mainActivity.tabViewModel.fragmentFiles[position]
    return TabFragment.newInstance(file).apply { tabFragments[position] = this }
  }
  
  override fun getItemId(position: Int): Long {
    if (!itemIds.containsKey(position)) {
      itemIds[position] = nextItemId++
    }
    return itemIds[position]!!
  }
  
  override fun containsItem(itemId: Long): Boolean {
    return itemIds.containsValue(itemId)
  }
  
  fun notifyItemRemovedX(position: Int) {
    // Shift all items after the removed position
    for (i in position until itemIds.size - 1) {
      itemIds[i] = itemIds[i + 1]!!
    }
    // Remove the last item
    itemIds.remove(itemIds.size - 1)
    tabFragments.remove(position)
    notifyItemRemoved(position)
  }
  
  fun notifyItemInsertedX(position: Int) {
    // Shift all items from the inserted position
    for (i in itemIds.size - 1 downTo position) {
      itemIds[i + 1] = itemIds[i]!!
    }
    // Add new item ID
    itemIds[position] = nextItemId++
    notifyItemInserted(position)
  }
  
  @SuppressLint("NotifyDataSetChanged")
  fun clearAllFragments() {
    with(mainActivity) {
      tabViewModel.fileSet.clear()
      tabViewModel.fragmentFiles.clear()
      tabFragments.clear()
      tabViewModel.fragmentTitles.clear()
      (viewPager.adapter as? TabAdapter)?.notifyDataSetChanged()
      binding.tabs.visibility = View.GONE
      binding.mainView.visibility = View.GONE
      binding.openBtn.visibility = View.VISIBLE
    }
  }
  
  fun removeFragment(position: Int) {
    with(mainActivity) {
      if (position >= 0 && position < tabViewModel.fragmentFiles.size) {
        tabViewModel.fileSet.remove(tabViewModel.fragmentFiles[position].absolutePath)
        MenuItemHandler.set.remove(tabViewModel.fragmentFiles[position].name)
        tabViewModel.fragmentFiles.removeAt(position)
        tabViewModel.fragmentTitles.removeAt(position)
        
        (viewPager.adapter as? TabAdapter)?.apply {
          notifyItemRemovedX(position)
        }
      }
      if (tabViewModel.fragmentFiles.isEmpty()) {
        binding.tabs.visibility = View.GONE
        binding.mainView.visibility = View.GONE
        binding.openBtn.visibility = View.VISIBLE
        binding.apply {
          divider.visibility = View.GONE
          mainBottomBar.visibility = View.GONE
        }
      }

      
    }
  }
  
  fun clearAllFragmentsExceptSelected() {
    mainActivity.lifecycleScope.launch(Dispatchers.Main) {
      val selectedTabPosition = mainActivity.tabLayout.selectedTabPosition
      
      // Iterate backwards to avoid index shifting issues when removing fragments
      for (i in mainActivity.tabLayout.tabCount - 1 downTo 0) {
        if (i != selectedTabPosition) {
          removeFragment(i)
        }
      }
    }
  }
  
  fun addFragment(file: File) {
    with(mainActivity) {
      if (tabViewModel.fileSet.contains(file.absolutePath)) {
        rkUtils.toast("File already opened")
        return
      }
      if (tabViewModel.fragmentFiles.size >= tabLimit) {
        rkUtils.toast("Cannot open more than $tabLimit files")
        return
      }
      tabViewModel.fileSet.add(file.absolutePath)
      tabViewModel.fragmentFiles.add(file)
      tabViewModel.fragmentTitles.add(file.name)
      (viewPager.adapter as? TabAdapter)?.notifyItemInsertedX(tabViewModel.fragmentFiles.size - 1)
      if (tabViewModel.fragmentFiles.size > 1) viewPager.setCurrentItem(
        tabViewModel.fragmentFiles.size - 1, true
      )
      binding.tabs.visibility = View.VISIBLE
      binding.mainView.visibility = View.VISIBLE
      binding.openBtn.visibility = View.GONE
      
      
      if (SettingsData.getBoolean(Keys.SHOW_ARROW_KEYS, false)) {
        binding.apply {
          divider.visibility = View.VISIBLE
          mainBottomBar.visibility = View.VISIBLE
        }
      } else {
        binding.apply {
          divider.visibility = View.GONE
          mainBottomBar.visibility = View.GONE
        }
      }

    }
  }
  
}