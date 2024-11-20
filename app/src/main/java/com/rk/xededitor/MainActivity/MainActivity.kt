package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.libcommons.After
import com.rk.xededitor.DefaultScope
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.MainActivity.file.TabSelectedListener
import com.rk.xededitor.MainActivity.handlers.MenuClickHandler
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.MainActivity.handlers.PermissionHandler
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.R
import com.rk.libcommons.SetupEditor
import com.rk.xededitor.databinding.ActivityTabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference

class MainActivity : BaseActivity() {
    
    companion object {
        var activityRef = WeakReference<MainActivity?>(null)
    }
    
    var binding: ActivityTabBinding? = null
    var viewPager: ViewPager2? = null
    var tabLayout: TabLayout? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    var fileManager:FileManager? = FileManager(this)
    var menu: Menu? = null
    var adapter: TabAdapter? = null
    val tabViewModel: TabViewModel by viewModels()
    val projectManager = ProjectManager()
    
    class TabViewModel : ViewModel() {
        val fragmentFiles = mutableListOf<File>()
        val fragmentTypes = mutableListOf<FragmentType>()
        val fragmentTitles = mutableListOf<String>()
        val fileSet = HashSet<String>()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityRef = WeakReference(this)
        binding = ActivityTabBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        
        setSupportActionBar(binding!!.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        
        lifecycleScope.launch { SetupEditor.init(this@MainActivity) }
        setupDrawer()
        
        setupViewPager()
        setupTabLayout()
        setupAdapter()
        projectManager.onCreate(this)
        projectManager.restoreProjects(this)
        ProjectBar.setupNavigationRail(this)
        
        if (tabViewModel.fragmentFiles.isNotEmpty()) {
            binding!!.tabs.visibility = View.VISIBLE
            binding!!.mainView.visibility = View.VISIBLE
            binding!!.openBtn.visibility = View.GONE
        }
        
        lifecycleScope.launch(Dispatchers.Default){
            while (true){
                delay(2000)
                MenuItemHandler.update(this@MainActivity)
            }
        }
        
        
        //testing
        After(5000){
            //Wasm3().loadWasm("/sdcard/main.wasm", "main")
        }
        
    }
    
    inline fun isMenuInitialized(): Boolean = menu != null
    
    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu
        
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        
        menu.findItem(R.id.action_add).isVisible = true
        return true
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHandler.onRequestPermissionsResult(requestCode, grantResults, this)
    }
    
    var isPaused = true
    override fun onPause() {
        isPaused = true
        super.onPause()
    }
    
    override fun onResume() {
        isPaused = false
        super.onResume()
        AutoSaver.start(this)
        lifecycleScope.launch { PermissionHandler.verifyStoragePermission(this@MainActivity) }
        projectManager.processQueue(this)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        
        if (id == android.R.id.home) {
            with(binding!!.drawerLayout) {
                val start = GravityCompat.START
                if (isDrawerOpen(start)) {
                    closeDrawer(start)
                } else {
                    openDrawer(start)
                }
            }
            return true
        } else {
            if (drawerToggle!!.onOptionsItemSelected(item)) {
                return true
            }
            MenuClickHandler.handle(this, item)
            return false
        }
    }
    
    private fun setupViewPager() {
        viewPager = binding!!.viewpager2.apply {
            // do not remove .toInt
            offscreenPageLimit = tabLimit.toInt()
            isUserInputEnabled = false
        }
    }
    
    private fun setupDrawer() {
        val drawerLayout = binding!!.drawerLayout
        drawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer)
        drawerLayout.addDrawerListener(drawerToggle!!)
        drawerToggle!!.syncState()
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding!!.drawerLayout.setScrimColor(Color.TRANSPARENT)
        binding!!.drawerLayout.setDrawerElevation(0f)
        binding!!.drawerLayout.addDrawerListener(
            object : DrawerLayout.DrawerListener {
                var leftDrawerOffset = 0f
                var rightDrawerOffset = 0f
                
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    val drawerWidth = drawerView.width
                    leftDrawerOffset = drawerWidth * slideOffset
                    binding!!.main.translationX = leftDrawerOffset
                }
                
                override fun onDrawerOpened(drawerView: View) {}
                
                override fun onDrawerClosed(drawerView: View) {
                    binding!!.main.translationX = 0f
                    leftDrawerOffset = 0f
                    rightDrawerOffset = 0f
                }
                
                override fun onDrawerStateChanged(newState: Int) {}
            }
        )
    }
    
    private fun setupTabLayout() {
        binding!!.tabs.addOnTabSelectedListener(TabSelectedListener(this@MainActivity))
        tabLayout = binding!!.tabs
    }
    
    private fun setupAdapter() {
        adapter = TabAdapter(this)
        viewPager!!.adapter = adapter
        
        TabLayoutMediator(tabLayout!!, viewPager!!) { tab, position ->
            tab.text = tabViewModel.fragmentTitles[position]
        }.attach()
    }
    
    fun openDrawer(v: View?) {
        binding!!.drawerLayout.open()
    }
    
    override fun onDestroy() {
        DefaultScope.cancel()
        super.onDestroy()
        projectManager.closeRemoteConnections()
        binding = null
        adapter = null
        viewPager = null
        fileManager = null
        menu = null
        activityRef = WeakReference(null)
        
    }
}
