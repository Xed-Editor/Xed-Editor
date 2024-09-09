package com.rk.xededitor.MainActivity

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.KeyboardUtils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.rk.libcommons.After
import com.rk.librunner.Runner
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.MainActivity.handlers.MenuClickHandler
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.MainActivity.handlers.PermissionHandler
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File


object ActivitySetup{

	fun init(activity: MainActivity){
		activity.lifecycleScope.launch(Dispatchers.Default){
			handleIntent(activity)
			setupTabClickListener(activity)
			SetupEditor.init(activity)
			PermissionHandler.verifyStoragePermission(activity)
			setupArrowKeys(activity)
			hideKeyBoardIfTooLarge(activity)
			setupNavigationRail(activity)

			delay(500)
			withContext(Dispatchers.Main){
				//OnBackPressedHandler(activity)
			}

			//FileManager.loadPreviouslyOpenedFiles(activity)
		}
	}

	private val openFileId = View.generateViewId()
	private val openDirId = View.generateViewId()
	private val openPathId = View.generateViewId()
    private val cloneRepo = View.generateViewId()

	private fun setupNavigationRail(activity: MainActivity){
		var dialog:AlertDialog? = null

		val listener = View.OnClickListener { v->
			when(v.id){
				openFileId -> {
					FileManager.openFile()
				}
				openDirId -> {
					FileManager.openDir()
				}
				openPathId -> {
					FileManager.openFromPath()
				}
                cloneRepo -> {
                    var dialog: AlertDialog? = null
                    val view = LayoutInflater.from(activity).inflate(R.layout.popup_new, null)
                    view.findViewById<LinearLayout>(R.id.mimeTypeEditor).visibility = View.VISIBLE
                    val repolinkedit = view.findViewById<EditText>(R.id.name).apply {
                        hint = "Repository git link"
                    }
                    val branchedit = view.findViewById<EditText>(R.id.mime).apply {
                        hint = "Branch"
                        setText("")
                    }
                    MaterialAlertDialogBuilder(activity).setTitle("Clone repository")
                        .setView(view).setNegativeButton("Cancel", null)
                        .setPositiveButton("Apply") { _, _ ->
                            val repoLink = repolinkedit.text.toString()
                            val branch = branchedit.text.toString()
                            val repoName = repoLink.substringAfterLast("/").removeSuffix(".git")
                            val repoDir = File(SettingsData.getString(Keys.GIT_REPO_DIR, "/storage/emulated/0") + "/" + repoName)
                            if (repoLink.isEmpty() || branch.isEmpty()) {
                                rkUtils.toast(activity, "Please fill in both fields")
                            }
                            else if (repoDir.exists()) {
                                rkUtils.toast(activity, "$repoDir already exists!")
                            }
                            else {
                                val loadingPopup = LoadingPopup(activity, null).setMessage("Cloning repository...")
                                loadingPopup.show()
                                GlobalScope.launch(Dispatchers.IO) {
                                    try {
                                        Git.cloneRepository().setURI(repoLink).setDirectory(repoDir).setBranch(branch).call()
                                        withContext(Dispatchers.Main) {
                                            loading.hide()
                                            ProjectManager.addProject(repoDir)
                                        }
                                    }
                                    catch (e: TransportException) {
                                        val credentials = SettingsData.getString(Keys.GIT_CRED, "").split(":")
                                        if (credentials.size != 2) {
                                            withContext(Dispatchers.Main) {
                                                loading.hide()
                                                rkUtils.toast(activity, "Repository is private. Check your credentials")
                                            }
                                        }
                                        else {
                                            try {
                                                Git.cloneRepository().setURI(repoLink).setDirectory(repoDir).setBranch(branch).setCredentialsProvider(UsernamePasswordCredentialsProvider(credentials[0], credentials[1])).call()
                                                withContext(Dispatchers.Main) {
                                                    loading.hide()
                                                    ProjectManager.addProject(repoDir)
                                                }
                                            }
                                            catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    loading.hide()
                                                    rkUtils.toast(activity, "Error: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }.show()
                }
			}
			dialog?.hide()
			dialog = null
		}

		fun handleAddNew(){
			ActionPopup(activity).apply {
				addItem("Open a File","Choose a file from storage to directly edit it",ContextCompat.getDrawable(activity,R.drawable.outline_insert_drive_file_24),listener,openFileId)
				addItem("Open a Directory","Choose a directory from storage as a project",ContextCompat.getDrawable(activity,R.drawable.outline_folder_24),listener,openDirId)
				addItem("Open from Path","Open a project/file from a path",ContextCompat.getDrawable(activity,R.drawable.android),listener,openPathId)
                addItem("Clone repository","Clone repository using Git",ContextCompat.getDrawable(activity,R.drawable.git),listener,cloneRepo)
				setTitle("Add")
				getDialogBuilder().setNegativeButton("Cancel",null)
				dialog = show()
			}

		}

		activity.binding.navigationRail.setOnItemSelectedListener { item ->
			if (item.itemId == R.id.add_new) {
				handleAddNew()
				false
			}
			else {
				ProjectManager.projects[item.itemId]?.let {
					ProjectManager.changeProject(File(it),activity)
				}
				true
			}
		}

		//close drawer if same item is selected again except add_new item
		activity.binding.navigationRail.setOnItemReselectedListener { item ->
			if (item.itemId == R.id.add_new) {
				handleAddNew()
			}
		}

	}


	var smoothScroll = true
	private fun setupTabClickListener(activity: MainActivity){
		smoothScroll = SettingsData.getBoolean(Keys.VIEWPAGER_SMOOTH_SCROLL,true)
		with(activity){
			mTabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
				override fun onTabSelected(tab: TabLayout.Tab) {

					viewPager?.setCurrentItem(tab.position, smoothScroll)

					val fragment = StaticData.fragments[mTabLayout.selectedTabPosition]
					fragment.updateUndoRedo()
					StaticData.menu?.findItem(R.id.git)?.setVisible(fragment.file != null && FileManager.findGitRoot(fragment.file) != null)
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
								activity.adapter?.notifyDataSetChanged()
							}

							R.id.close_others -> {
								adapter?.closeOthers(viewPager!!.currentItem)
								activity.adapter?.notifyDataSetChanged()
							}

							R.id.close_all -> {
								adapter?.clear()
								activity.adapter?.notifyDataSetChanged()
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

	fun handleIntent(activity: MainActivity){
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
	fun setupArrowKeys(activity: MainActivity){
		rkUtils.runOnUiThread {
			val arrows = activity.binding.childs

			val tabSize = SettingsData.getString(Keys.TAB_SIZE, "4").toInt()
			val useSpaces = SettingsData.getBoolean(Keys.USE_SPACE_INTABS, true)


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

							val columm = if (uplinestr.length < cursor.leftColumn) {
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

							val columm = if (dnlinestr.length < cursor.leftColumn) {
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

						val tabSize = SettingsData.getString(Keys.TAB_SIZE, "4").toInt()

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