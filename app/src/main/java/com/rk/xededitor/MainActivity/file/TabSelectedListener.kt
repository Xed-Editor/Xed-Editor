package com.rk.xededitor.MainActivity.file

import androidx.appcompat.widget.PopupMenu
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.currentTab
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.R
import java.lang.ref.WeakReference

var smoothTabs = PreferencesData.getBoolean(PreferencesKeys.VIEWPAGER_SMOOTH_SCROLL, false)

class TabSelectedListener(val activity: MainActivity) : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: Tab?) {
        currentTab = WeakReference(tab)
        if (smoothTabs.not()) { activity.viewPager!!.setCurrentItem(tab!!.position, false) }
        MenuItemHandler.update(activity)
        tab?.text = tab?.text
    }
    
    override fun onTabReselected(tab: Tab?) {
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
            MenuItemHandler.update(activity)
            
            true
        }
        popupMenu.show()
    }
    override fun onTabUnselected(tab: Tab?) {}
}