package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.rk.libcommons.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MenuClickHandler.Companion.handle
import com.rk.xededitor.MainActivity.MenuClickHandler.Companion.hideSearchMenuItems
import com.rk.xededitor.MainActivity.MenuClickHandler.Companion.showSearchMenuItems
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.StaticData.menu
import com.rk.xededitor.MainActivity.fragment.AutoSaver
import com.rk.xededitor.MainActivity.fragment.DynamicFragment
import com.rk.xededitor.MainActivity.fragment.NoSwipeViewPager
import com.rk.xededitor.MainActivity.fragment.TabAdapter
import com.rk.xededitor.MainActivity.treeview2.FileAction
import com.rk.xededitor.MainActivity.treeview2.PrepareRecyclerView
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityMainBinding
import com.rk.xededitor.rkUtils
import java.io.File

class MainActivity : BaseActivity() {
	var binding: ActivityMainBinding? = null
	var adapter: TabAdapter? = null
	var viewPager: NoSwipeViewPager? = null
	var drawerLayout: DrawerLayout? = null
	private var navigationView: NavigationView? = null
	private var drawerToggle: ActionBarDrawerToggle? = null
	
	
	
	override fun onCreate(savedInstanceState: Bundle?) {
		StaticData.clear()
		super.onCreate(savedInstanceState)
		
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding!!.root)
		
		setSupportActionBar(binding!!.toolbar)
		supportActionBar!!.setDisplayHomeAsUpEnabled(true)
		supportActionBar!!.setDisplayShowTitleEnabled(false)
		
		setupDrawer()
		PrepareRecyclerView(this)
		MainActivityAsync(this)
		
		initiateStaticVariables()
		
	}
	
	
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		PermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
	}
	
	
	private fun setupDrawer() {
		drawerLayout = binding!!.drawerLayout
		navigationView = binding!!.navView
		navigationView?.layoutParams?.width = (Resources.getSystem().displayMetrics.widthPixels * 0.87).toInt()
		
		drawerToggle = ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer)
		drawerLayout?.addDrawerListener(drawerToggle!!)
		drawerToggle?.syncState()
		drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
		
	}
	
	private fun initiateStaticVariables() {
		viewPager = binding!!.viewpager
		mTabLayout = binding!!.tabs
		viewPager!!.setOffscreenPageLimit(15)
		mTabLayout.setupWithViewPager(viewPager)
		
		if (adapter == null) {
			StaticData.fragments = ArrayList()
			adapter = TabAdapter(supportFragmentManager)
			viewPager!!.setAdapter(adapter)
		}
	}
	
	
	@Deprecated("Deprecated in Java")
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		
		if (resultCode == RESULT_OK) {
			if (data != null) {
				if (requestCode == StaticData.REQUEST_FILE_SELECTION) {
					FileManager.handleFileSelection(data, this)
				} else if (requestCode == StaticData.REQUEST_DIRECTORY_SELECTION) {
					FileManager.handleDirectorySelection(data, this)
				}
			}
			when (requestCode) {
				StaticData.MANAGE_EXTERNAL_STORAGE -> {
					PermissionManager.verifyStoragePermission(this)
				}
				
				FileAction.REQUEST_CODE_OPEN_DIRECTORY -> {
					FileManager.handleOpenDirectory(data, this)
				}
				
				FileAction.REQUEST_ADD_FILE -> {
					FileManager.handleAddFile(data, this)
				}
			}
		}
	}
	
	
	fun newEditor(file: File, text: String? = null) {
		for (f in StaticData.fragments) {
			if (f.file == file) {
				rkUtils.toast(this, "File already opened!")
				return
			}
		}
		
		val fragment = DynamicFragment(file, this)
		if (text != null) {
			fragment.editor.setText(text)
		}
		adapter!!.addFragment(fragment, file)
		
		for (i in 0 until mTabLayout.tabCount) {
			val tab = mTabLayout.getTabAt(i)
			if (tab != null) {
				val name = StaticData.fragments[tab.position].fileName
				tab.setText(name)
			}
		}
		
		
		updateMenuItems()
		if (!AutoSaver.isRunning()) {
			AutoSaver()
		}
	}
	
	
	fun openFile(v: View?) {
		FileManager.openFile()
	}
	fun openDir(v: View?) {
		FileManager.openDir()
	}
	fun reselectDir(v: View?) {
		FileManager.openDir()
	}
	fun fileOptions(v: View?) {
		FileAction(this, StaticData.rootFolder, StaticData.rootFolder, null)
	}
	fun openDrawer(v: View?) {
		drawerLayout!!.open()
	}
	fun openFromPath(v: View?) {
		FileManager.openFromPath()
	}
	fun privateDir(v: View?) {
		FileManager.privateDir()
	}
	
	@SuppressLint("RestrictedApi")
	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		StaticData.menu = menu
		
		if (menu is MenuBuilder) {
			menu.setOptionalIconsVisible(true)
		}
		
		updateMenuItems()
		return true
	}
	
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		val id = item.itemId
		
		if (id == android.R.id.home) {
			if (drawerLayout!!.isDrawerOpen(GravityCompat.START)) {
				drawerLayout!!.closeDrawer(GravityCompat.START)
			} else {
				drawerLayout!!.openDrawer(GravityCompat.START)
			}
			return true
		} else {
            if (drawerToggle!!.onOptionsItemSelected(item)) {
                return true
            }
			return handle(this, item)
		}
	}
	
	companion object {
		fun updateMenuItems() {
			val visible = !(StaticData.fragments == null || StaticData.fragments.isEmpty())
			if (menu == null) {
				After(200) { rkUtils.runOnUiThread { updateMenuItems() } }
				return
			}
			val activity = checkNotNull(getActivity(MainActivity::class.java))
			if (mTabLayout == null || mTabLayout.selectedTabPosition == -1) {
				hideSearchMenuItems()
			} else if (!StaticData.fragments[mTabLayout.selectedTabPosition].isSearching) {
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
				menu.findItem(R.id.insertdate).setVisible(visible)
				
				val shouldShowUndoRedo = visible && !StaticData.fragments[mTabLayout.selectedTabPosition].isSearching
				findItem(R.id.undo).setVisible(shouldShowUndoRedo)
				findItem(R.id.redo).setVisible(shouldShowUndoRedo)
			}
			
			
			
			if (visible && SettingsData.getBoolean(SettingsData.Keys.SHOW_ARROW_KEYS, false)) {
				activity.binding!!.divider.visibility = View.VISIBLE
				activity.binding!!.mainBottomBar.visibility = View.VISIBLE
				val vp = activity.binding!!.viewpager
				val layoutParams = vp.layoutParams as RelativeLayout.LayoutParams
				layoutParams.bottomMargin = rkUtils.dpToPx(44f, activity)
				vp.layoutParams = layoutParams
			} else {
				activity.binding!!.divider.visibility = View.GONE
				activity.binding!!.mainBottomBar.visibility = View.GONE
				val vp = activity.binding!!.viewpager
				val layoutParams = vp.layoutParams as RelativeLayout.LayoutParams
				layoutParams.bottomMargin = rkUtils.dpToPx(0f, activity)
				vp.layoutParams = layoutParams
			}
		}
	}
	
	
	override fun onDestroy() {
		StaticData.clear()
		super.onDestroy()
	}
}
