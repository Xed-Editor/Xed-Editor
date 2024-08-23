package com.rk.xededitor.MainActivity

import android.view.View
import android.widget.RelativeLayout
import com.rk.libcommons.After
import com.rk.librunner.Runner
import com.rk.xededitor.BaseActivity.Companion.getActivity
import com.rk.xededitor.MainActivity.MenuClickHandler.hideSearchMenuItems
import com.rk.xededitor.MainActivity.MenuClickHandler.showSearchMenuItems
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.StaticData.menu
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils

object MenuItemHandler {
	fun updateMenuItems() {
		val visible = !(fragments == null || fragments.isEmpty())
		if (menu == null) {
			After(200) { rkUtils.runOnUiThread { updateMenuItems() } }
			return
		}
		
		if (mTabLayout == null || mTabLayout.selectedTabPosition == -1) {
			hideSearchMenuItems()
		} else if (!fragments[mTabLayout.selectedTabPosition].isSearching) {
			hideSearchMenuItems()
		} else {
			showSearchMenuItems()
		}
		
		with(menu){
			findItem(R.id.batchrep).setVisible(visible)
			findItem(R.id.search).setVisible(visible)
			findItem(R.id.action_save).setVisible(visible)
			findItem(R.id.action_print).setVisible(visible)
			findItem(R.id.action_all).setVisible(visible)
			findItem(R.id.batchrep).setVisible(visible)
			findItem(R.id.search).setVisible(visible)
			findItem(R.id.share).setVisible(visible)
			findItem(R.id.insertdate).setVisible(visible)
			
			
			val shouldShowUndoRedo = visible && !fragments[mTabLayout.selectedTabPosition].isSearching
			findItem(R.id.undo).setVisible(shouldShowUndoRedo)
			findItem(R.id.redo).setVisible(shouldShowUndoRedo)
			
			val shouldShowRun = visible && fragments[mTabLayout.selectedTabPosition].file != null
					&& Runner.isRunnable(fragments[mTabLayout.selectedTabPosition].file!!)
			findItem(R.id.run).setVisible(shouldShowRun)
		}
		
		
		val activity = checkNotNull(getActivity(MainActivity::class.java))
		if (visible && SettingsData.getBoolean(SettingsData.Keys.SHOW_ARROW_KEYS, false)) {
			with(activity.binding){
				divider.visibility = View.VISIBLE
				mainBottomBar.visibility = View.VISIBLE
				
				(viewpager.layoutParams as RelativeLayout.LayoutParams).apply {
					bottomMargin = rkUtils.dpToPx(44f, activity)
					viewpager.layoutParams = this
				}
			}
			
		} else {
			with(activity.binding){
				divider.visibility = View.GONE
				mainBottomBar.visibility = View.GONE
				
				(viewpager.layoutParams as RelativeLayout.LayoutParams).apply {
					bottomMargin = rkUtils.dpToPx(0f, activity)
					viewpager.layoutParams = this
				}
			}
			
		}
	}
	
}