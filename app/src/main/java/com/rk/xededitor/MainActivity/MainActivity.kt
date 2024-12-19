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
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.editor.ControlPanel
import com.rk.resources.drawables
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.MainActivity.file.TabSelectedListener
import com.rk.xededitor.MainActivity.handlers.MenuClickHandler
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.MainActivity.handlers.PermissionHandler
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.R
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.databinding.ActivityTabBinding
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.lang.ref.WeakReference


class MainActivity : BaseActivity() {
    
    companion object {
        var activityRef = WeakReference<MainActivity?>(null)
        fun withContext(invoke:MainActivity.()->Unit){
            activityRef.get()?.let { invoke(it) }
        }
    }
    
    var binding: ActivityTabBinding? = null
    var viewPager: ViewPager2? = null
    var tabLayout: TabLayout? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    var fileManager:FileManager? = FileManager(this)
    var menu: Menu? = null
    var adapter: TabAdapter? = null
    val tabViewModel: TabViewModel by viewModels()
    
    
    class TabViewModel : ViewModel() {
        val fragmentFiles = mutableListOf<File>()
        val fragmentTypes = mutableListOf<FragmentType>()
        val fragmentTitles = mutableListOf<String>()
        val fileSet = HashSet<String>()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this);
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun showControlPanel(c: ControlPanel){ showControlPanel(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityRef = WeakReference(this)
        binding = ActivityTabBinding.inflate(layoutInflater)
        setContentView(binding!!.root)


        setSupportActionBar(binding!!.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        setupDrawer()
        
        setupViewPager()
        setupTabLayout()
        setupAdapter()
        
        ProjectManager.restoreProjects(this)
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
        
        
    }
    
    inline fun isMenuInitialized(): Boolean = menu != null


    val toolItems = hashSetOf<Int>()
    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu
        
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        
        menu.findItem(R.id.action_add).isVisible = true


        val tool = ContextCompat.getDrawable(this,drawables.build_24px)
        var order = 0
        Mutators.getMutators().forEach { menu.findItem(R.id.tools).subMenu?.add(1,it.hashCode(),order,it.name)?.icon = tool;order++;toolItems.add(it.hashCode()) }

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
                    if (PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false)) {
kotlin.runCatching { saveAllFiles() }
}
        
        super.onPause()
    }

    private fun saveAllFiles(){
        if (tabViewModel.fragmentFiles.isNotEmpty()) {

                adapter?.tabFragments?.values?.forEach { weakRef ->
                    weakRef.get()?.fragment?.let { fragment ->
                        if (fragment is EditorFragment) {
                            fragment.save(showToast = false, isAutoSaver = true)
                        }
                    }
                }
        }
    }
    
    override fun onResume() {
        isPaused = false
        super.onResume()
        lifecycleScope.launch { PermissionHandler.verifyStoragePermission(this@MainActivity) }
        ProjectManager.processQueue(this)
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
            ActionBarDrawerToggle(this, drawerLayout, strings.open_drawer, strings.close_drawer)
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
        if (PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false)) {
kotlin.runCatching { saveAllFiles() }
}
        
        DefaultScope.cancel()
        super.onDestroy()
        binding = null
        adapter = null
        viewPager = null
        fileManager = null
        menu = null
        activityRef = WeakReference(null)
    }
}
