package com.rk.xededitor.MainActivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.rk.libcommons.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.FileClipboard.Companion.clear
import com.rk.xededitor.MainActivity.MenuClickHandler.Companion.handle
import com.rk.xededitor.MainActivity.MenuClickHandler.Companion.hideSearchMenuItems
import com.rk.xededitor.MainActivity.MenuClickHandler.Companion.showSearchMenuItems
import com.rk.xededitor.MainActivity.PathUtils.convertUriToPath
import com.rk.xededitor.MainActivity.fragment.AutoSaver
import com.rk.xededitor.MainActivity.fragment.DynamicFragment
import com.rk.xededitor.MainActivity.fragment.NoSwipeViewPager
import com.rk.xededitor.MainActivity.fragment.TabAdapter
import com.rk.xededitor.MainActivity.treeview2.FileAction
import com.rk.xededitor.MainActivity.treeview2.FileAction.Companion.Staticfile
import com.rk.xededitor.MainActivity.treeview2.FileAction.Companion.to_save_file
import com.rk.xededitor.MainActivity.treeview2.PrepareRecyclerView
import com.rk.xededitor.MainActivity.treeview2.TreeView
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter.Companion.stopThread
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityMainBinding
import com.rk.xededitor.rkUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MainActivity : BaseActivity() {
    var binding: ActivityMainBinding? = null
    var adapter: TabAdapter? = null
    var viewPager: NoSwipeViewPager? = null
    var drawerLayout: DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    private var isReselecting = false


    override fun onCreate(savedInstanceState: Bundle?) {
        StaticData.clear()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)


        setSupportActionBar(binding!!.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        drawerLayout = binding!!.drawerLayout
        navigationView = binding!!.navView
        navigationView!!.layoutParams.width =
            (Resources.getSystem().displayMetrics.widthPixels * 0.87).toInt()


        drawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer)
        drawerLayout!!.addDrawerListener(drawerToggle!!)
        drawerToggle!!.syncState()
        drawerLayout!!.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        PrepareRecyclerView(this)

        rkUtils.verifyStoragePermission(this)

        //run async init
        Init(this)


        //prepare tablayout and viewpager
        viewPager = binding!!.viewpager
        StaticData.mTabLayout = binding!!.tabs
        viewPager!!.setOffscreenPageLimit(15)
        StaticData.mTabLayout.setupWithViewPager(viewPager)

        if (adapter == null) {
            StaticData.fragments = ArrayList()
            adapter = TabAdapter(supportFragmentManager)
            viewPager!!.setAdapter(adapter)
        }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //check permission for old devices
        if (requestCode == StaticData.REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // permission denied
                rkUtils.verifyStoragePermission(this)
            }
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            if (data != null) {
                if (requestCode == StaticData.REQUEST_FILE_SELECTION) {
                    handleFileSelection(data)
                } else if (requestCode == StaticData.REQUEST_DIRECTORY_SELECTION) {
                    handleDirectorySelection(data)
                }
            }
            when (requestCode) {
                StaticData.MANAGE_EXTERNAL_STORAGE -> {
                    rkUtils.verifyStoragePermission(this)
                }

                FileAction.REQUEST_CODE_OPEN_DIRECTORY -> {
                    handleOpenDirectory(data)
                }

                FileAction.REQUEST_ADD_FILE -> {
                    handleAddFile(data)
                }
            }
        }

    }

    private fun handleAddFile(data: Intent?) {
        val selectedFile = File(convertUriToPath(this, data!!.data))
        val targetFile = Staticfile

        if (targetFile != null && targetFile.isDirectory && selectedFile.exists() && selectedFile.isFile) {
            try {
                val destinationPath = File(targetFile, selectedFile.name).toPath()
                Files.move(
                    selectedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING
                )
                if (targetFile.absolutePath == StaticData.rootFolder.absolutePath) {
                    TreeView(this, StaticData.rootFolder)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("FileAction", "Failed to move file: " + e.message)
            }
        }
    }

    private fun handleOpenDirectory(data: Intent?) {
        val directoryUri = data!!.data

        if (directoryUri != null) {
            val directory = File(convertUriToPath(this, directoryUri))
            if (directory.isDirectory) {
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val newFile = File(directory, to_save_file!!.name)

                try {
                    Files.copy(
                        to_save_file!!.toPath(),
                        newFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )

                    //clear file clipboard
                    clear()
                } catch (e: IOException) {
                    e.printStackTrace()
                    throw RuntimeException("Failed to save file: " + e.message)
                }
            } else {
                throw RuntimeException("Selected path is not a directory")
            }
        } else {
            Toast.makeText(this, "No directory selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDirectorySelection(data: Intent) {
        binding!!.mainView.visibility = View.VISIBLE
        binding!!.safbuttons.visibility = View.GONE
        binding!!.maindrawer.visibility = View.VISIBLE
        binding!!.drawerToolbar.visibility = View.VISIBLE

        val file = File(convertUriToPath(this, data.data))
        StaticData.rootFolder = file
        var name = StaticData.rootFolder.name
        if (name.length > 18) {
            name = StaticData.rootFolder.name.substring(0, 15) + "..."
        }
        binding!!.rootDirLabel.text = name
        TreeView(this@MainActivity, file)
    }

    private fun handleFileSelection(data: Intent) {
        binding!!.tabs.visibility = View.VISIBLE
        binding!!.mainView.visibility = View.VISIBLE
        binding!!.openBtn.visibility = View.GONE
        newEditor(File(convertUriToPath(this, data.data)))
    }

    fun onNewEditor() {
        binding!!.openBtn.visibility = View.GONE
        binding!!.tabs.visibility = View.VISIBLE
        binding!!.mainView.visibility = View.VISIBLE
        updateMenuItems()
    }

    @JvmOverloads
    fun newEditor(file: File, text: String? = null) {
        for (f in StaticData.fragments) {
            if (f.file == file) {
                rkUtils.toast(this, "File already opened!")
                return
            }
        }


        val dynamicfragment = DynamicFragment(file, this)
        if (text != null) {
            dynamicfragment.editor.setText(text)
        }
        adapter!!.addFragment(dynamicfragment, file)

        for (i in 0 until StaticData.mTabLayout.tabCount) {
            val tab = StaticData.mTabLayout.getTabAt(i)
            if (tab != null) {
                val name = StaticData.fragments[tab.position].fileName
                tab.setText(name)
            }
        }

        updateMenuItems()
        if (!AutoSaver.isRunning()){
            AutoSaver()
        }
    }


    fun openFile(v: View?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        startActivityForResult(intent, StaticData.REQUEST_FILE_SELECTION)
    }

    fun openDir(v: View?) {
        stopThread()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, StaticData.REQUEST_DIRECTORY_SELECTION)
    }

    fun reselctDir(v: View?) {
        isReselecting = true
        openDir(null)
    }

    fun fileOptions(v: View?) {
        FileAction(this@MainActivity, StaticData.rootFolder, StaticData.rootFolder, null)
    }


    fun openDrawer(v: View?) {
        drawerLayout!!.open()
    }

    fun openFromPath(v: View?) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<View>(R.id.name) as EditText

        editText.setText(Environment.getExternalStorageDirectory().absolutePath)
        editText.hint = "file or folder path"

        MaterialAlertDialogBuilder(this).setView(popupView).setTitle("Path").setNegativeButton(
            getString(
                R.string.cancel
            ), null
        ).setPositiveButton("Open", DialogInterface.OnClickListener { dialog, which ->
            val path = editText.text.toString()
            if (path.isEmpty()) {
                rkUtils.toast(this@MainActivity, "Please enter a path")
                return@OnClickListener
            }
            val file = File(path)
            if (!file.exists()) {
                rkUtils.toast(this@MainActivity, "Path does not exist")
                return@OnClickListener
            }

            if (!file.canRead() && file.canWrite()) {
                rkUtils.toast(this@MainActivity, "Permission Denied")
            }
            if (file.isDirectory) {
                binding!!.mainView.visibility = View.VISIBLE
                binding!!.safbuttons.visibility = View.GONE
                binding!!.maindrawer.visibility = View.VISIBLE
                binding!!.drawerToolbar.visibility = View.VISIBLE

                StaticData.rootFolder = file

                TreeView(this@MainActivity, file)

                //use new file browser
                var name = StaticData.rootFolder.name
                if (name.length > 18) {
                    name = StaticData.rootFolder.name.substring(0, 15) + "..."
                }

                binding!!.rootDirLabel.text = name
            } else {
                newEditor(file)
            }
        }).show()
    }

    fun privateDir(v: View?) {
        binding!!.mainView.visibility = View.VISIBLE
        binding!!.safbuttons.visibility = View.GONE
        binding!!.maindrawer.visibility = View.VISIBLE
        binding!!.drawerToolbar.visibility = View.VISIBLE

        val file = filesDir.parentFile

        StaticData.rootFolder = file

        if (file != null) {
            TreeView(this@MainActivity, file)
        }

        //use new file browser
        var name = StaticData.rootFolder.name
        if (name.length > 18) {
            name = StaticData.rootFolder.name.substring(0, 15) + "..."
        }

        binding!!.rootDirLabel.text = name
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        StaticData.menu = menu

        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }

        menu.findItem(R.id.search)
            .setVisible(!(StaticData.fragments == null || StaticData.fragments.isEmpty()))
        menu.findItem(R.id.batchrep)
            .setVisible(!(StaticData.fragments == null || StaticData.fragments.isEmpty()))

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
            if (StaticData.menu == null) {
                After(200) { rkUtils.runOnUiThread { updateMenuItems() } }
                return
            }
            val activity = checkNotNull(
                getActivity(
                    MainActivity::class.java
                )
            )
            if (StaticData.mTabLayout == null || StaticData.mTabLayout.selectedTabPosition == -1) {
                hideSearchMenuItems()
            } else if (!StaticData.fragments[StaticData.mTabLayout.selectedTabPosition].isSearching) {
                hideSearchMenuItems()
            } else {
                showSearchMenuItems()
            }

            StaticData.menu.findItem(R.id.batchrep).setVisible(visible)
            StaticData.menu.findItem(R.id.search).setVisible(visible)
            StaticData.menu.findItem(R.id.action_save).setVisible(visible)
            StaticData.menu.findItem(R.id.action_print).setVisible(visible)
            StaticData.menu.findItem(R.id.action_all).setVisible(visible)
            StaticData.menu.findItem(R.id.batchrep).setVisible(visible)
            StaticData.menu.findItem(R.id.search).setVisible(visible)
            StaticData.menu.findItem(R.id.share).setVisible(visible)
            val shouldShowUndoRedo =
                visible && !StaticData.fragments[StaticData.mTabLayout.selectedTabPosition].isSearching
            StaticData.menu.findItem(R.id.undo).setVisible(shouldShowUndoRedo)
            StaticData.menu.findItem(R.id.redo).setVisible(shouldShowUndoRedo)
            StaticData.menu.findItem(R.id.insertdate).setVisible(visible)


            if (visible && SettingsData.getBoolean(SettingsData.Keys.SHOW_ARROW_KEYS, false)) {
                activity.binding!!.divider.visibility = View.VISIBLE
                activity.binding!!.mainBottomBar.visibility = View.VISIBLE
                val vp = activity.binding!!.viewpager
                val layoutParams = vp.layoutParams as RelativeLayout.LayoutParams
                layoutParams.bottomMargin =
                    rkUtils.dpToPx(44f, activity)
                vp.layoutParams = layoutParams
            } else {
                activity.binding!!.divider.visibility = View.GONE
                activity.binding!!.mainBottomBar.visibility = View.GONE
                val vp = activity.binding!!.viewpager
                val layoutParams = vp.layoutParams as RelativeLayout.LayoutParams
                layoutParams.bottomMargin =
                    rkUtils.dpToPx(0f, activity)
                vp.layoutParams = layoutParams
            }
        }
    }


    override fun onDestroy() {
        StaticData.clear()
        super.onDestroy()
    }
}
