package com.rk.xededitor.MainActivity

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.rk.xededitor.After
import com.rk.xededitor.Decompress
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.treeview2.TreeView
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import java.io.File

class Init(activity: MainActivity) {
  init {
    Thread {
      Thread.currentThread().priority = 10
      with(activity) {


        if (!SettingsData.isDarkMode(this)) {
          //light mode
          window.navigationBarColor = Color.parseColor("#FEF7FF")
          val decorView = window.decorView
          var flags = decorView.systemUiVisibility
          flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
          decorView.systemUiVisibility = flags
        } else if (SettingsData.isDarkMode(this)) {

          if (SettingsData.isOled(this)) {

            binding.drawerLayout.setBackgroundColor(Color.BLACK)
            binding.navView.setBackgroundColor(Color.BLACK)
            binding.main.setBackgroundColor(Color.BLACK)
            binding.appbar.setBackgroundColor(Color.BLACK)
            binding.toolbar.setBackgroundColor(Color.BLACK)
            binding.tabs.setBackgroundColor(Color.BLACK)
            binding.mainView.setBackgroundColor(Color.BLACK)
            val window = window
            window.navigationBarColor = Color.BLACK
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.BLACK
          } else {
            val window = window
            window.navigationBarColor = Color.parseColor("#141118")
          }

        }


        mTabLayout.setOnTabSelectedListener(object : OnTabSelectedListener {
          override fun onTabSelected(tab: TabLayout.Tab) {
            viewPager.setCurrentItem(tab.position)
            StaticData.fragments[mTabLayout.selectedTabPosition].updateUndoRedo()
          }

          override fun onTabUnselected(tab: TabLayout.Tab) {}
          override fun onTabReselected(tab: TabLayout.Tab) {
            val popupMenu = PopupMenu(activity, tab.view)
            val inflater = popupMenu.menuInflater
            inflater.inflate(R.menu.tab_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
              val id = item.itemId
              if (id == R.id.close_this) {
                adapter.removeFragment(mTabLayout.selectedTabPosition)
              } else if (id == R.id.close_others) {
                adapter.closeOthers(viewPager.currentItem)
              } else if (id == R.id.close_all) {
                adapter.clear()
              }
              for (i in 0 until mTabLayout.tabCount) {
                val tab = mTabLayout.getTabAt(i)
                if (tab != null) {
                  val name = StaticData.fragments[i].fileName
                  if (name != null) {
                    tab.setText(name)
                  }
                }
              }
              if (mTabLayout.tabCount < 1) {
                binding.tabs.visibility = View.GONE
                binding.mainView.visibility = View.GONE
                binding.openBtn.visibility = View.VISIBLE
              }
              MainActivity.updateMenuItems()
              true
            }
            popupMenu.show()
          }
        })

        //todo use shared prefs instead of files
        if (!File(getExternalFilesDir(null).toString() + "/unzip").exists()) {
          Thread {
            try {
              Decompress.unzipFromAssets(
                this@with,
                "files.zip",
                getExternalFilesDir(null).toString() + "/unzip"
              )
              File(getExternalFilesDir(null).toString() + "files").delete()
              File(getExternalFilesDir(null).toString() + "files.zip").delete()
              File(getExternalFilesDir(null).toString() + "textmate").delete()
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }.start()


        }

        val last_opened_path = SettingsData.getSetting(this, "lastOpenedPath", "")
        if (last_opened_path.isNotEmpty()) {
          binding.mainView.visibility = View.VISIBLE
          binding.safbuttons.visibility = View.GONE
          binding.maindrawer.visibility = View.VISIBLE
          binding.drawerToolbar.visibility = View.VISIBLE

          StaticData.rootFolder = File(last_opened_path)

          runOnUiThread { TreeView(this, StaticData.rootFolder) }

          var name = StaticData.rootFolder.name
          if (name.length > 18) {
            name = StaticData.rootFolder.name.substring(0, 15) + "..."
          }
          binding.rootDirLabel.text = name
        }


        // val uriString = SettingsData.getSetting(this, "lastOpenedUri", "null")
        /* if (uriString != "null") {
           val uri = Uri.parse(uriString)
           if (hasUriPermission(uri)) {
             StaticData.rootFolder = DocumentFile.fromTreeUri(this, uri)
             //binding.tabs.setVisibility(View.VISIBLE);
             binding.mainView.visibility = View.VISIBLE
             binding.safbuttons.visibility = View.GONE
             binding.maindrawer.visibility = View.VISIBLE
             binding.drawerToolbar.visibility = View.VISIBLE

             runOnUiThread { TreeView(this, StaticData.rootFolder) }

             var name = StaticData.rootFolder.name!!
             if (name.length > 18) {
               name = StaticData.rootFolder.name!!.substring(0, 15) + "..."
             }
             binding.rootDirLabel.text = name
           }
         }*/

      }

      After(
        1000
      ) {
        rkUtils.runOnUiThread {
          activity.onBackPressedDispatcher.addCallback(activity,
            object : OnBackPressedCallback(true) {
              override fun handleOnBackPressed() {


                //close drawer if opened
                if (activity.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                  activity.drawerLayout.closeDrawer(GravityCompat.START)
                  return
                }


                var shouldExit = true

                var isModified = false
                if (StaticData.fragments != null) {
                  for (fragment in StaticData.fragments) {

                    if (fragment.isModified) {
                      isModified = true
                    }
                  }
                  if (isModified) {
                    shouldExit = false
                    val dialog: MaterialAlertDialogBuilder =
                      MaterialAlertDialogBuilder(activity).setTitle(
                        activity.getString(R.string.unsaved)
                      ).setMessage(activity.getString(R.string.unsavedfiles))
                        .setNegativeButton(
                          activity.getString(R.string.cancel),
                          null
                        ).setPositiveButton(
                          activity.getString(R.string.exit)
                        ) { dialogInterface: DialogInterface?, i: Int -> activity.finish() }


                    dialog.setNeutralButton(
                      activity.getString(R.string.saveexit)
                    ) { xdialog: DialogInterface?, which: Int ->
                      activity.onOptionsItemSelected(
                        StaticData.menu.findItem(
                          R.id.action_all
                        )
                      )
                      activity.finish()
                    }



                    dialog.show()
                  }
                }
                if (shouldExit) {
                  activity.finish()
                }
              }
            })
        }
      }

      val intent: Intent = activity.intent
      val type = intent.type

      if (Intent.ACTION_SEND == intent.action && type != null) {
        if (type.startsWith("text")) {
          val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
          if (sharedText != null) {
            val file = File(activity.externalCacheDir, "newfile.txt")

            rkUtils.runOnUiThread {
              activity.newEditor(file, true, sharedText)
            }


            After(
              150
            ) {
              rkUtils.runOnUiThread { activity.onNewEditor() }
            }
          }
        }
      }


      rkUtils.runOnUiThread {
        val arrows = MainActivity.activity.binding.childs
        for (i in 0 until arrows.childCount) {
          val button = arrows.getChildAt(i)
          button.setOnClickListener { v ->
            val fragment = StaticData.fragments[mTabLayout.selectedTabPosition]
            val cursor = fragment.editor.cursor
            when (v.id) {
              R.id.left_arrow -> {
                if (cursor.leftColumn - 1 >= 0) {
                  fragment.editor.setSelection(
                    cursor.leftLine,
                    cursor.leftColumn - 1
                  )
                }
              }

              R.id.right_arrow -> {
                val lineNumber = cursor.leftLine
                val line = fragment.content!!.getLine(lineNumber)

                if (cursor.leftColumn < line.length) {
                  fragment.editor.setSelection(
                    cursor.leftLine,
                    cursor.leftColumn + 1
                  )

                }

              }

              R.id.up_arrow -> {
                if (cursor.leftLine - 1 >= 0) {
                  val upline = cursor.leftLine - 1
                  val uplinestr = fragment.content!!.getLine(upline)

                  var columm = 0

                  if (uplinestr.length < cursor.leftColumn) {
                    columm = uplinestr.length
                  } else {
                    columm = cursor.leftColumn
                  }


                  fragment.editor.setSelection(
                    cursor.leftLine - 1,
                    columm
                  )
                }

              }

              R.id.down_arrow -> {
                if (cursor.leftLine + 1 < fragment.content!!.lineCount) {

                  val dnline = cursor.leftLine + 1
                  val dnlinestr = fragment.content!!.getLine(dnline)

                  var columm = 0

                  if (dnlinestr.length < cursor.leftColumn) {
                    columm = dnlinestr.length
                  } else {
                    columm = cursor.leftColumn
                  }

                  fragment.editor.setSelection(
                    cursor.leftLine + 1,
                    columm
                  )
                }
              }
              R.id.tab -> {fragment.editor.insertText("    ",4)}
              R.id.home -> {
                fragment.editor.setSelection(cursor.leftLine, 0)
              }
              R.id.end -> {
                val line = fragment.content!!.getLine(cursor.leftLine)
                fragment.editor.setSelection(cursor.leftLine, line.length)
              }
            }
          }
        }
      }


    }.start()
  }
}