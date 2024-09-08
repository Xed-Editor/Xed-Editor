package com.rk.xededitor.tab

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator


import com.rk.xededitor.MainActivity.ActionPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.file.FileManager




import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.databinding.ActivityTabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class TabActivity : AppCompatActivity() {
    lateinit var binding: ActivityTabBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var fm: FM

    val fragmentFiles = mutableListOf<File>()
    private val fragmentTitles = mutableListOf<String>()

    private val TAB_LIMIT = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        setupTheme()
        setupDrawer()

        //todo remove this
        SetupEditor.init(this)

        setupViewPager()
        setupTabLayout()


        fm = FM(this)
        setupNavigationRail()

        if (savedInstanceState == null){
            repeat(4) {
                addFragment(File(filesDir.parentFile, "proot.sh"))
            }
        }else{
            restoreState(savedInstanceState)
        }


        setupAdapter()
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        StaticData.menu = menu

        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }

        //MenuItemHandler.updateMenuItems()
        return true
    }

    var isPaused = true

    override fun onPause() {
        isPaused = true
        super.onPause()
    }

    override fun onResume() {
        isPaused = false

        ProjectManager.processQueue(this)
        super.onResume()
    }

    private fun setupNavigationRail(){
        val openFileId = View.generateViewId()
        val openDirId = View.generateViewId()
        val openPathId = View.generateViewId()
        val privateFilesId = View.generateViewId()

        var dialog: AlertDialog? = null

        val listener = View.OnClickListener { v->
            when(v.id){
                openFileId -> {
                    fm.requestOpenFile()
                }
                openDirId -> {
                    fm.requestOpenDirectory()
                }
                openPathId -> {
                    fm.requestOpenFromPath()
                }
                privateFilesId -> {
                    ProjectManager.addProject(this,filesDir.parentFile!!)
                }
            }
            dialog?.hide()
            dialog = null
        }

        fun handleAddNew(){
            ActionPopup(this).apply {
                addItem("Open a File","Choose a file from storage to directly edit it",
                    ContextCompat.getDrawable(this@TabActivity,R.drawable.outline_insert_drive_file_24),listener,openFileId)
                addItem("Open a Directory","Choose a directory from storage as a project",
                    ContextCompat.getDrawable(this@TabActivity,R.drawable.outline_folder_24),listener,openDirId)
                addItem("Open from Path","Open a project/file from a path",
                    ContextCompat.getDrawable(this@TabActivity,R.drawable.android),listener,openPathId)

                addItem("Private Files","Private files of karbon",ContextCompat.getDrawable(this@TabActivity,R.drawable.android),listener,privateFilesId)


                setTitle("Add")
                getDialogBuilder().setNegativeButton("Cancel",null)
                dialog = show()
            }

        }

        binding.navigationRail.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.add_new) {
                handleAddNew()
                false
            }
            else {
                ProjectManager.projects[item.itemId]?.let {
                    ProjectManager.changeProject(File(it),this)
                }
                true
            }
        }

        //close drawer if same item is selected again except add_new item
        binding.navigationRail.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.add_new) {
                handleAddNew()
            }
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == android.R.id.home) {
            with(binding.drawerLayout) {
                val start = GravityCompat.START
                if (isDrawerOpen(start)) {
                    closeDrawer(start)
                } else {
                    openDrawer(start)
                }
            }
            return true
        } else {
            if (drawerToggle.onOptionsItemSelected(item)) {
                return true
            }
           // return handle(this, item)
            return false
        }
    }

    private fun setupViewPager() {
        viewPager = binding.viewpager2.apply {
            offscreenPageLimit = TAB_LIMIT
            isUserInputEnabled = false
        }
    }


    private fun setupTheme() {
        if (SettingsData.isDarkMode(this)) {
            if (SettingsData.isOled()) {
                val black = Color.BLACK
                with(binding) {
                    drawerLayout.setBackgroundColor(black)
                    navView.setBackgroundColor(black)
                    main.setBackgroundColor(black)
                    appbar.setBackgroundColor(black)
                    toolbar.setBackgroundColor(black)
                    tabs.setBackgroundColor(black)
                    mainView.setBackgroundColor(black)
                }
            } else {
                val window = window
                window.navigationBarColor = Color.parseColor("#141118")
            }
        }
    }

    private fun setupDrawer() {
        val drawerLayout = binding.drawerLayout
        val navigationView = binding.navView
        navigationView.layoutParams?.width =
            (Resources.getSystem().displayMetrics.widthPixels * 0.87).toInt()
        drawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

    }
    private fun setupTabLayout() {
        tabLayout = binding.tabs.apply {
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: Tab?) {
                    viewPager.setCurrentItem(tab!!.position, false)
                }

                override fun onTabUnselected(tab: Tab?) {}
                override fun onTabReselected(tab: Tab?) {
                    val popupMenu = PopupMenu(this@TabActivity, tab!!.view)
                    popupMenu.menuInflater.inflate(R.menu.tab_menu, popupMenu.menu)
                    popupMenu.setOnMenuItemClickListener { item ->
                        val id = item.itemId
                        when (id) {
                            R.id.close_this -> {
                                removeFragment(tab.position)
                            }

                            R.id.close_others -> {
                                clearAllFragmentsExceptSelected()
                            }

                            R.id.close_all -> {
                                clearAllFragments()
                            }
                        }

                        true
                    }
                    popupMenu.show()
                }
            })
        }
    }

    private fun setupAdapter() {
        val adapter = FragmentAdapter(this, lifecycle)
        viewPager.adapter = adapter


        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = fragmentTitles[position]
        }.attach()

    }

    private fun restoreState(state: Bundle) {
        fragmentFiles.clear()
        fragmentTitles.clear()
        @Suppress("DEPRECATION")
        state.getSerializable("fileUris")?.let {
            @Suppress("UNCHECKED_CAST")
            fragmentFiles.addAll(it as List<File>)
        }
        state.getStringArrayList("titles")?.let { fragmentTitles.addAll(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("fileUris", ArrayList(fragmentFiles))
        outState.putStringArrayList("titles", ArrayList(fragmentTitles))
    }
    //view click listeners
//    fun openFile(v: View?) {
//        FileManager.openFile()
//    }

    fun openDrawer(v: View?) {
        binding.drawerLayout.open()
    }

    // ----------------------------------------------------------------------------

    //adapter related stuff
    fun addFragment(file: File) {
        fragmentFiles.add(file)
        fragmentTitles.add(file.name)
        (viewPager.adapter as? FragmentAdapter)?.notifyItemInsertedX(fragmentFiles.size - 1)
    }

    private fun removeFragment(position: Int) {
        if (position >= 0 && position < fragmentFiles.size) {
            fragmentFiles.removeAt(position)
            fragmentTitles.removeAt(position)

            (viewPager.adapter as? FragmentAdapter)?.apply {
                notifyItemRemovedX(position)
            }

        }

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearAllFragments() {
        fragmentFiles.clear()
        fragmentTitles.clear()
        (viewPager.adapter as? FragmentAdapter)?.notifyDataSetChanged()
    }

    private fun clearAllFragmentsExceptSelected() {
        lifecycleScope.launch(Dispatchers.Main) {
            val selectedTabPosition = tabLayout.selectedTabPosition

            // Iterate backwards to avoid index shifting issues when removing fragments
            for (i in tabLayout.tabCount - 1 downTo 0) {
                if (i != selectedTabPosition) {
                    removeFragment(i)
                }
            }
        }
    }

    private var nextItemId = 0L
    inner class FragmentAdapter(activity: AppCompatActivity, lifecycle: Lifecycle) :
        FragmentStateAdapter(activity.supportFragmentManager, lifecycle) {
        private val itemIds = mutableMapOf<Int, Long>()

        override fun getItemCount(): Int = fragmentFiles.size

        override fun createFragment(position: Int): Fragment {
            val file = fragmentFiles[position]
            return TabFragment.newInstance(file)
        }

        override fun getItemId(position: Int): Long {
            if (!itemIds.containsKey(position)) {
                itemIds[position] = nextItemId++
            }
            return itemIds[position]!!
        }

        override fun containsItem(itemId: Long): Boolean {
            return itemIds.containsValue(itemId)
        }

        fun notifyItemRemovedX(position: Int) {
            // Shift all items after the removed position
            for (i in position until itemIds.size - 1) {
                itemIds[i] = itemIds[i + 1]!!
            }
            // Remove the last item
            itemIds.remove(itemIds.size - 1)

            notifyItemRemoved(position)
        }

        fun notifyItemInsertedX(position: Int) {
            // Shift all items from the inserted position
            for (i in itemIds.size - 1 downTo position) {
                itemIds[i + 1] = itemIds[i]!!
            }
            // Add new item ID
            itemIds[position] = nextItemId++

            notifyItemInserted(position)
        }
    }
}
