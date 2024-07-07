package com.rk.xededitor.MainActivity

import android.content.DialogInterface
import android.content.Intent
import android.content.UriPermission
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.rk.xededitor.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.HandleMenuClick.Companion.handle
import com.rk.xededitor.MainActivity.treeview2.HandleFileActions
import com.rk.xededitor.MainActivity.treeview2.HandleFileActions.Companion.REQUEST_CODE_OPEN_DIRECTORY
import com.rk.xededitor.MainActivity.treeview2.HandleFileActions.Companion.saveFile
import com.rk.xededitor.MainActivity.treeview2.MA
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter.Companion.stopThread
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityMainBinding
import com.rk.xededitor.rkUtils
import java.io.File
import java.io.FileInputStream

class MainActivity : BaseActivity() {
  
  private val REQUEST_FILE_SELECTION = 123
  lateinit var binding: ActivityMainBinding
  var adapter: mAdapter? = null
  lateinit var viewPager: ViewPager
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawerToggle: ActionBarDrawerToggle
  private var isReselecting = false
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.getRoot())
    Data.activity = this
    
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowTitleEnabled(false)
    
    drawerLayout = binding.drawerLayout
    drawerToggle =
      ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer)
    drawerLayout.addDrawerListener(drawerToggle)
    drawerToggle.syncState()
    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    
    
    After(3000) {
      runOnUiThread {
        onBackPressedDispatcher.addCallback(this@MainActivity,
          object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
              var shouldExit = true
              if (Data.fragments != null) {
                for (fragment in Data.fragments) {
                  if (fragment.isModified) {
                    shouldExit = false
                    MaterialAlertDialogBuilder(this@MainActivity).setTitle("Unsaved Files")
                      .setMessage("You have unsaved files!").setNegativeButton("Cancel", null)
                      .setNeutralButton(
                        "Save & Exit"
                      ) { _: DialogInterface?, _: Int ->
                        onOptionsItemSelected(Data.menu.findItem(R.id.action_all))
                        finish()
                      }.setPositiveButton(
                        "Exit"
                      ) { _: DialogInterface?, i: Int -> finish() }.show()
                  }
                  break
                }
              }
              if (shouldExit) {
                finish()
              }
            }
          })
      }
    }
    viewPager = binding.viewpager
    Data.mTabLayout = binding.tabs
    viewPager.setOffscreenPageLimit(15)
    Data.mTabLayout.setupWithViewPager(viewPager)
    
    Data.mTabLayout.setOnTabSelectedListener(object : OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab) {
        viewPager.setCurrentItem(tab.position)
        rkUtils.getCurrentFragment().updateUndoRedo()
      }
      
      override fun onTabUnselected(tab: TabLayout.Tab) {}
      override fun onTabReselected(tab: TabLayout.Tab) {
        val popupMenu = PopupMenu(Data.activity, tab.view)
        val inflater = popupMenu.menuInflater
        inflater.inflate(R.menu.tab_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
          when (item.itemId) {
            R.id.close_this -> {
              adapter?.removeFragment(Data.mTabLayout.selectedTabPosition)
            }
            
            R.id.close_others -> {
              adapter?.closeOthers(viewPager.currentItem)
            }
            
            R.id.close_all -> {
              adapter?.clear()
            }
          }
          
          for (i in 0 until Data.mTabLayout.tabCount) {
            val tab1 = Data.mTabLayout.getTabAt(i)
            if (tab1 != null) {
              val name = Data.titles[i]
              if (name != null) {
                tab1.setText(name)
              }
            }
          }
          if (Data.mTabLayout.tabCount < 1) {
            binding.tabs.visibility = View.GONE
            binding.mainView.visibility = View.GONE
            binding.openBtn.visibility = View.VISIBLE
          }
          updateEditorViews()
          true
        }
        popupMenu.show()
      }
    })
    Init(this)
  }
  
  
  fun hasUriPermission(uri: Uri?): Boolean {
    if (uri == null) return false
    val persistedPermissions = contentResolver.persistedUriPermissions
    return persistedPermissions.stream().anyMatch { p: UriPermission -> p.uri == uri }
  }
  
  private fun updateEditorViews() {
    val visible = !(Data.fragments == null || Data.fragments.isEmpty())
    if (visible) {
      binding.openBtn.visibility = View.GONE
      binding.tabs.visibility = View.GONE
      binding.mainView.visibility = View.GONE
    } else {
      binding.openBtn.visibility = View.VISIBLE
      binding.tabs.visibility = View.VISIBLE
      binding.mainView.visibility = View.VISIBLE
    }
    Data.menu.findItem(R.id.search).setVisible(visible)
    Data.menu.findItem(R.id.action_save).setVisible(visible)
    Data.menu.findItem(R.id.action_print).setVisible(visible)
    Data.menu.findItem(R.id.action_all).setVisible(visible)
    Data.menu.findItem(R.id.batchrep).setVisible(visible)
    Data.menu.findItem(R.id.share).setVisible(visible)
    Data.menu.findItem(R.id.undo).setVisible(true)
    Data.menu.findItem(R.id.redo).setVisible(true)
  }
  
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // Handle the theme change here
    rkUtils.toast("Please restart to properly change theme")
  }
  
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (requestCode == REQUEST_FILE_SELECTION && resultCode == RESULT_OK && data != null) {
      val selectedFileUri = data.data
      binding.tabs.visibility = View.VISIBLE
      binding.mainView.visibility = View.VISIBLE
      binding.openBtn.visibility = View.GONE
      newEditor(selectedFileUri?.let { DocumentFile.fromSingleUri(this, it) }, false)
    } else if (requestCode == Data.REQUEST_DIRECTORY_SELECTION && resultCode == RESULT_OK && data != null) {
      Thread{
        binding.mainView.visibility = View.VISIBLE
        binding.safbuttons.visibility = View.GONE
        binding.maindrawer.visibility = View.VISIBLE
        binding.drawerToolbar.visibility = View.VISIBLE
        val treeUri = data.data
        persistUriPermission(treeUri)
        Data.rootFolder = treeUri?.let { DocumentFile.fromTreeUri(this, it) }
        //use new file browser
        runOnUiThread { MA(this, Data.rootFolder) }
        
      
        var name = Data.rootFolder.name
        if (name != null) {
          if (name.length > 18) {
            name = (name.substring(0, 15) ?: "") + "..."
            
          }
        }
        runOnUiThread { binding.rootDirLabel.text = name }
        
      }.start()
      
    
    } else if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
      //this block will run when user tries to save anew file
      val directoryUri = data?.data
      if (directoryUri != null) {
        // Save a file in the selected directory
        saveFile(this, directoryUri)
      } else {
        Toast.makeText(this, "No directory selected", Toast.LENGTH_SHORT).show()
      }
    } else if (requestCode == Data.REQUEST_CODE_CREATE_FILE && resultCode == RESULT_OK) {
      if (data != null) {
        val uri = data.data
        if (uri != null) {
          Thread {
            val cacheFile = File(externalCacheDir, "newfile.txt")
            try {
              FileInputStream(cacheFile).use { inputStream ->
                contentResolver.openOutputStream(uri, "wt").use { outputStream ->
                  val buffer = ByteArray(1024)
                  var length: Int
                  while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream?.write(buffer, 0, length)
                  }
                  cacheFile.delete()
                }
              }
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }.start()
        }
      }
    }
  }
  
  /*this method is called when user opens a file
  unlike newEditor which is called when opening a directory via file manager*/
  fun onNewEditor() {
    updateEditorViews()
  }
  
  fun onEditorRemove(fragment: DynamicFragment) {
    fragment.releaseEditor()
    if (Data.fragments.size <= 1) {
      Data.menu.findItem(R.id.undo).setVisible(false)
      Data.menu.findItem(R.id.redo).setVisible(false)
    }
  }
  
  fun newEditor(file: DocumentFile?, isNewFile: Boolean) {/* if (adapter == null) {
      Data.fragments = ArrayList()
      Data.titles = ArrayList()
      Data.uris = ArrayList()
      adapter = mAdapter(supportFragmentManager)
      viewPager.setAdapter(adapter)
    }
    val file_name = file?.name
    if (Data.fileList.contains(file) || adapter!!.addFragment(
        DynamicFragment(
          file, Data.activity, isNewFile
        ), file_name, file
      )
    ) {
      rkUtils.toast("File already opened!")
      return
    }
    
    }*/
    Thread {
      if (adapter == null) {
        Data.fragments = ArrayList()
        Data.titles = ArrayList()
        Data.uris = ArrayList()
        adapter = mAdapter(supportFragmentManager)
        viewPager.setAdapter(adapter)
      }
      val fileName = file?.name
      if (Data.fileList.contains(file) || adapter!!.addFragment(DynamicFragment(file, Data.activity, isNewFile), fileName, file)) {
        rkUtils.toast("File already opened!")
      } else {
        Data.fileList.add(file)
        for (i in 0 until Data.mTabLayout.tabCount) {
          val tab = Data.mTabLayout.getTabAt(i)
          if (tab != null) {
            val name = Data.titles[tab.position]
            runOnUiThread { tab.setText(name) }
          }
        }
      }
      runOnUiThread { updateEditorViews() }
      
    }.start()
    
  }
  
  
  override fun onDestroy() {
    Data.clear()
    super.onDestroy()
  }
  
  
  fun openFile(v: View?) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType("*/*") // you can specify mime types here to filter certain types of files
    startActivityForResult(intent, REQUEST_FILE_SELECTION)
  }
  
  private fun persistUriPermission(uri: Uri?) {
    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    // Check if URI permission is already granted
    if (contentResolver.persistedUriPermissions.stream()
        .noneMatch { p: UriPermission -> p.uri == uri }
    ) {
      if (uri != null) {
        contentResolver.takePersistableUriPermission(uri, takeFlags)
      }
    }
    SettingsData.setSetting(this, "lastOpenedUri", uri.toString())
  }
  
  fun revokeUriPermission(uri: Uri?) {
    val releaseFlags =
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    if (uri != null) {
      contentResolver.releasePersistableUriPermission(uri, releaseFlags)
    }
  }
  
  fun openDir(v: View?) {
    stopThread()
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    startActivityForResult(intent, Data.REQUEST_DIRECTORY_SELECTION)
  }
  
  fun reselctDir(v: View?) {
    isReselecting = true
    val uriStr = SettingsData.getSetting(this, "lastOpenedUri", "null")
    if (!uriStr.isEmpty() && uriStr != "null") {
      revokeUriPermission(Uri.parse(uriStr))
      SettingsData.setSetting(this, "lastOpenedUri", "null")
    }
    openDir(null)
  }
  
  fun fileOptions(v: View?) {
    if (v != null) {
      HandleFileActions(
        this, Data.rootFolder, Data.rootFolder, v
      )
    }
  }
  
  
  fun openDrawer(v: View?) {
    drawerLayout.open()
  }
  
  fun newFile(v: View?) {
    newEditor(DocumentFile.fromFile(File(externalCacheDir, "newfile.txt")), true)
    onNewEditor()
    After(500) { drawerLayout.close() }
  }
  
  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    Data.menu = menu
    menu.findItem(R.id.search).setVisible(!(Data.fragments == null || Data.fragments.isEmpty()))
    menu.findItem(R.id.batchrep).setVisible(!(Data.fragments == null || Data.fragments.isEmpty()))
    return true
  }
  
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val id = item.itemId
    //this is used to open and close the drawer
    return if (id == androidx.appcompat.R.id.home) {
      if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
        drawerLayout.closeDrawer(GravityCompat.START)
      } else {
        drawerLayout.openDrawer(GravityCompat.START)
      }
      true
    } else {
      if (drawerToggle.onOptionsItemSelected(item)) {
        true
      } else handle(item)
    }
  }
}


