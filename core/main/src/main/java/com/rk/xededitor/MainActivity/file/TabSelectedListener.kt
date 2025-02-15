package com.rk.xededitor.MainActivity.file

import androidx.appcompat.widget.PopupMenu
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.libcommons.DefaultScope
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.currentTab
import com.rk.xededitor.MainActivity.handlers.updateMenu
import com.rk.xededitor.R
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

var smoothTabs = Settings.smooth_tabs

class TabSelectedListener(val activity: MainActivity) : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: Tab?) {
        currentTab = WeakReference(tab)
        if (smoothTabs.not()) { activity.viewPager!!.setCurrentItem(tab!!.position, false) }
        tab?.text = tab?.text
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }
    }
    
    override fun onTabReselected(tab: Tab?) {
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

        val popupMenu = PopupMenu(activity, tab!!.view)
        popupMenu.menuInflater.inflate(R.menu.tab_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            val id = item.itemId
            when (id) {
                R.id.close_this -> {
                    activity.adapter!!.removeFragment(tab.position,true)
                }
                
                R.id.close_others -> {
                    activity.adapter!!.clearAllFragmentsExceptSelected()
                }
                
                R.id.close_all -> {
                    activity.adapter!!.clearAllFragments()
                }
            }
            activity.binding!!.tabs.invalidate()
            activity.binding!!.tabs.requestLayout()
            
            // Detach and re-attach the TabLayoutMediator
            TabLayoutMediator(activity.binding!!.tabs, activity.viewPager!!) { tab, position ->
                tab.text = activity.tabViewModel.fragmentTitles[position]
            }
                .attach()
            DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }


            true
        }
        popupMenu.show()
    }
    override fun onTabUnselected(tab: Tab?) {

    }
}