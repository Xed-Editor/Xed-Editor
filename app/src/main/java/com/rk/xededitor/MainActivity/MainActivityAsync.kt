package com.rk.xededitor.MainActivity

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.PopupMenu
import com.blankj.utilcode.util.KeyboardUtils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.rk.libcommons.After
import com.rk.librunner.Runner
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.treeview2.TreeView
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import java.io.File

object MainActivityAsync{
	fun init(activity: MainActivity){
		Thread {
			handleIntent(activity)
			openLastPath(activity)
			setupTabClickListener(activity)
			SetupEditor.init(activity)
			PermissionManager.verifyStoragePermission(activity)
			setupArrowKeys(activity)
			hideKeyBoardIfTooLarge(activity)
		}.apply {
			priority = Thread.MAX_PRIORITY
			start()
		}
	}
	
	private fun setupTabClickListener(activity: MainActivity){
		with(activity){
			mTabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
				override fun onTabSelected(tab: TabLayout.Tab) {
					viewPager?.setCurrentItem(tab.position)
					val fragment = StaticData.fragments[mTabLayout.selectedTabPosition]
					fragment.updateUndoRedo()
					StaticData.menu?.findItem(R.id.run)?.setVisible(fragment.file != null && Runner.isRunnable(fragment.file!!))
					
					if (!fragment.isSearching) {
						MenuClickHandler.hideSearchMenuItems()
					} else {
						//show search buttons
						MenuClickHandler.showSearchMenuItems()
					}
					
					
				}
				
				override fun onTabReselected(tab: TabLayout.Tab) {
					val popupMenu = PopupMenu(activity, tab.view)
					popupMenu.menuInflater.inflate(R.menu.tab_menu, popupMenu.menu)
					
					popupMenu.setOnMenuItemClickListener { item ->
						val id = item.itemId
						when (id) {
							R.id.close_this -> {
								adapter?.removeFragment(mTabLayout.selectedTabPosition)
							}
							
							R.id.close_others -> {
								adapter?.closeOthers(viewPager!!.currentItem)
							}
							
							R.id.close_all -> {
								adapter?.clear()
								StaticData.menu?.findItem(R.id.run)?.setVisible(false)
								
							}
						}
						
						
						for (i in 0 until mTabLayout.tabCount) {
							mTabLayout.getTabAt(i)?.setText(StaticData.fragments[i].fileName)
						}
						
						
						if (mTabLayout.tabCount < 1) {
							binding.tabs.visibility = View.GONE
							binding.mainView.visibility = View.GONE
							binding.openBtn.visibility = View.VISIBLE
						}
						MenuItemHandler.updateMenuItems()
						true
					}
					popupMenu.show()
				}
				
				override fun onTabUnselected(tab: TabLayout.Tab) {}
			})
		}
		
	}
	private fun openLastPath(activity: MainActivity){
		val lastOpenedPath = SettingsData.getString(SettingsData.Keys.LAST_OPENED_PATH, "")
		if (lastOpenedPath.isNotEmpty()) {
			val file = File(lastOpenedPath)
			if (file.exists()) {
				rkUtils.runOnUiThread {
					with(activity.binding) {
						mainView.visibility = View.VISIBLE
						safbuttons.visibility = View.GONE
						maindrawer.visibility = View.VISIBLE
						drawerToolbar.visibility = View.VISIBLE
					}
				}
				StaticData.rootFolder = File(lastOpenedPath)
				activity.binding.rootDirLabel.text = StaticData.rootFolder.name.let {
					if (it.length > 18) it.substring(0, 15) + "..." else it
				}
				
				rkUtils.runOnUiThread { TreeView(activity, StaticData.rootFolder) }
			}
		}
	}
	private fun handleIntent(activity: MainActivity){
		val intent: Intent = activity.intent
		val type = intent.type
		
		if (Intent.ACTION_SEND == intent.action && type != null) {
			if (type.startsWith("text")) {
				val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
				if (sharedText != null) {
					val file = File(activity.externalCacheDir, "newfile.txt")
					
					rkUtils.runOnUiThread {
						activity.newEditor(file, sharedText)
					}
					
					After(150) {
						rkUtils.runOnUiThread { activity.adapter?.onNewEditor() }
					}
				}
			}
		}
	}
	private fun hideKeyBoardIfTooLarge(activity: MainActivity){
		rkUtils.runOnUiThread {
			val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			val rotation = windowManager.defaultDisplay.rotation
			activity.binding.root.viewTreeObserver.addOnGlobalLayoutListener {
				val r = Rect()
				activity.binding.root.getWindowVisibleDisplayFrame(r)
				val screenHeight = activity.binding.root.rootView.height
				val keypadHeight = screenHeight - r.bottom
				
				if (keypadHeight > screenHeight * 0.30) {
					if (rotation != Surface.ROTATION_0 && rotation != Surface.ROTATION_180) {
						KeyboardUtils.hideSoftInput(activity)
						rkUtils.toast(activity, "can't open keyboard in horizontal mode")
					}
				}
			}
		}
	}
	private fun setupArrowKeys(activity: MainActivity){
		rkUtils.runOnUiThread {
			val arrows = activity.binding.childs
			
			val tabSize = SettingsData.getString(SettingsData.Keys.TAB_SIZE, "4").toInt()
			val useSpaces = SettingsData.getBoolean(SettingsData.Keys.USE_SPACE_INTABS, true)
			
			
			val listener = View.OnClickListener { v ->
				val fragment = StaticData.fragments[mTabLayout.selectedTabPosition]
				val cursor = fragment.editor.cursor
				
				when (v.id) {
					R.id.left_arrow -> {
						if (cursor.leftColumn - 1 >= 0) {
							fragment.editor.setSelection(cursor.leftLine, cursor.leftColumn - 1)
						}
					}
					
					R.id.right_arrow -> {
						val lineNumber = cursor.leftLine
						val line = fragment.content!!.getLine(lineNumber)
						
						if (cursor.leftColumn < line.length) {
							fragment.editor.setSelection(cursor.leftLine, cursor.leftColumn + 1)
							
						}
						
					}
					
					R.id.up_arrow -> {
						if (cursor.leftLine - 1 >= 0) {
							val upline = cursor.leftLine - 1
							val uplinestr = fragment.content!!.getLine(upline)
							
							var columm = 0
							
							columm = if (uplinestr.length < cursor.leftColumn) {
								uplinestr.length
							} else {
								cursor.leftColumn
							}
							
							
							fragment.editor.setSelection(cursor.leftLine - 1, columm)
						}
						
					}
					
					R.id.down_arrow -> {
						if (cursor.leftLine + 1 < fragment.content!!.lineCount) {
							
							val dnline = cursor.leftLine + 1
							val dnlinestr = fragment.content!!.getLine(dnline)
							
							var columm = 0
							
							columm = if (dnlinestr.length < cursor.leftColumn) {
								dnlinestr.length
							} else {
								cursor.leftColumn
							}
							
							fragment.editor.setSelection(cursor.leftLine + 1, columm)
						}
					}
					
					R.id.tab -> {
						
						if (useSpaces) {
							val sb = StringBuilder()
							for (xi in 0 until tabSize) {
								sb.append(" ")
							}
							fragment.editor.insertText(sb.toString(), tabSize)
						} else {
							fragment.editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
						}
						
					}
					
					
					R.id.untab -> {
						if (cursor.leftColumn == 0) {
							return@OnClickListener
						}
						
						if (cursor.leftColumn >= tabSize) {
							fragment.editor.deleteText()
						}
						
					}
					
					R.id.home -> {
						fragment.editor.setSelection(cursor.leftLine, 0)
					}
					
					R.id.end -> {
						fragment.editor.setSelection(cursor.leftLine, fragment.content?.getLine(cursor.leftLine)?.length ?: 0)
					}
				}
				
			}
			
			for (i in 0 until arrows.childCount) {
				arrows.getChildAt(i).setOnClickListener(listener)
			}
			
		}
	}
}