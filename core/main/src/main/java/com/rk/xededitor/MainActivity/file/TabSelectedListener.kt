package com.rk.xededitor.MainActivity.file

import androidx.appcompat.widget.PopupMenu
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
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
        if (smoothTabs.not()) {
            activity.viewPager!!.setCurrentItem(tab!!.position, false)
        }
        tab?.text = tab?.text
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

        tab?.view?.setOnLongClickListener { view ->
            onTabReselected(tab)
            true
        }
    }

    override fun onTabReselected(tab: Tab?) {
        DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

        val view = tab?.view

        if (view == null) {
            errorDialog(strings.unknown_err.getString())
            return
        }

        val popupMenu = PopupMenu(activity, view)
        popupMenu.menuInflater.inflate(R.menu.tab_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            val id = item.itemId
            when (id) {
                R.id.close_this -> {
                    activity.adapter!!.removeFragment(tab.position, true)
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
                val titles = activity.tabViewModel.fragmentTitles
                if (position in titles.indices) {
                    tab.text = titles[position]
                } else {
                    toast("${strings.unknown_err} ${strings.restart_app}")
                }
            }.attach()
            DefaultScope.launch { updateMenu(MainActivity.activityRef.get()?.adapter?.getCurrentFragment()) }

            MainActivity.withContext {
                for (i in 0 until tabViewModel.fragmentTitles.size) {
                    tabLayout!!.getTabAt(i)?.text = tabViewModel.fragmentTitles[i]
                }
            }

            true
        }
        popupMenu.show()
    }

    override fun onTabUnselected(tab: Tab?) {

    }
}