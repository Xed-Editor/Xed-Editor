package com.rk.xededitor.MainActivity


import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.xededitor.BaseActivity
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.editor.AutoSaver
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.MainActivity.handlers.MenuClickHandler
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.MainActivity.handlers.PermissionHandler
import com.rk.xededitor.R
import com.rk.settings.PreferencesData
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.databinding.ActivityTabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Queue


class MainActivity : BaseActivity() {
  
  companion object {
    var activityRef = WeakReference<MainActivity?>(null)
  }
  
  lateinit var binding: ActivityTabBinding
  lateinit var viewPager: ViewPager2
  lateinit var tabLayout: TabLayout
  lateinit var drawerToggle: ActionBarDrawerToggle
  var fm = FileManager(this)
  lateinit var menu: Menu
  var smoothTabs = PreferencesData.getBoolean(PreferencesKeys.VIEWPAGER_SMOOTH_SCROLL, true)
  lateinit var adapter: TabAdapter
  private val pausedQueue: Queue<Runnable> = LinkedList()
  
  fun postPausedQueue(runnable: Runnable) {
    pausedQueue.add(runnable)
  }
  
  class TabViewModel : ViewModel() {
    val fragmentFiles = mutableListOf<File>()
    val fragmentTitles = mutableListOf<String>()
    val fileSet = HashSet<String>()
  }
  
  val tabViewModel: TabViewModel by viewModels()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityRef = WeakReference(this)
    binding = ActivityTabBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    setSupportActionBar(binding.toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    supportActionBar!!.setDisplayShowTitleEnabled(false)
    
    setupTheme()
    setupDrawer()
    
    setupViewPager()
    setupTabLayout()
    setupAdapter()
    
    ProjectManager.restoreProjects(this)
    ProjectBar.setupNavigationRail(this)
    
    if (tabViewModel.fragmentFiles.isNotEmpty()) {
      binding.tabs.visibility = View.VISIBLE
      binding.mainView.visibility = View.VISIBLE
      binding.openBtn.visibility = View.GONE
    }
    
    BottomBar.setupBottomBar(this)

    

  }
  
  @JvmOverloads
  fun addMenu(title:String,icon:Drawable? = ContextCompat.getDrawable(this
  ,R.drawable.extension),runnable: Runnable):MenuItem?{
    var item:MenuItem? = null
    lifecycleScope.launch(Dispatchers.Default){
      //wait until menu is available
      while (isMenuInitialized().not()){
        delay(800)
      }
      withContext(Dispatchers.Main){
        item = menu.add(title).apply {
          setIcon(icon)
          setOnMenuItemClickListener {
            Thread(runnable).start()
            return@setOnMenuItemClickListener false }
        }
      }
    }
    return item
  }
  
  
  fun isAdapterInitialized(): Boolean {
    return this::adapter.isInitialized
  }
  
  fun isMenuInitialized(): Boolean {
    return this::menu.isInitialized
  }
  
  @SuppressLint("RestrictedApi")
  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_tab_activity, menu)
    this.menu = menu
    
    if (menu is MenuBuilder) {
      menu.setOptionalIconsVisible(true)
    }
    return true
  }
  
  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    PermissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
  }
  
  var isPaused = true
  override fun onPause() {
    isPaused = true
    super.onPause()
  }
  
  override fun onResume() {
    isPaused = false
    lifecycleScope.launch(Dispatchers.Main) {
      while (pausedQueue.isNotEmpty()) {
        delay(100)
        withContext(Dispatchers.Main) {
          pausedQueue.poll()?.run()
        }
      }
    }
    super.onResume()
    AutoSaver.start(this)
    PermissionHandler.verifyStoragePermission(this)
    ProjectManager.processQueue(this)
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
      MenuClickHandler.handle(this, item)
      return false
    }
  }
  
  private fun setupViewPager() {
    viewPager = binding.viewpager2.apply {
      //do not remove .toInt
      offscreenPageLimit = tabLimit.toInt()
      isUserInputEnabled = false
    }
  }
  
  private fun setupTheme() {
    SetupEditor.init(this)
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
          currentTab = WeakReference(tab)
          if (smoothTabs.not()) {
            viewPager.setCurrentItem(tab!!.position, false)
          }
          MenuItemHandler.update(this@MainActivity)
          tab?.text = tab?.text
        }
        
        override fun onTabUnselected(tab: Tab?) {}
        
        override fun onTabReselected(tab: Tab?) {
          val popupMenu = PopupMenu(this@MainActivity, tab!!.view)
          popupMenu.menuInflater.inflate(R.menu.tab_menu, popupMenu.menu)
          popupMenu.setOnMenuItemClickListener { item ->
            val id = item.itemId
            when (id) {
              R.id.close_this -> {
                adapter.removeFragment(tab.position)
              }
              
              R.id.close_others -> {
                adapter.clearAllFragmentsExceptSelected()
              }
              
              R.id.close_all -> {
                adapter.clearAllFragments()
                
              }
            }
            binding.tabs.invalidate()
            binding.tabs.requestLayout()
            
            // Detach and re-attach the TabLayoutMediator
            TabLayoutMediator(binding.tabs, viewPager) { tab, position ->
              tab.text = tabViewModel.fragmentTitles[position]
            }.attach()
            MenuItemHandler.update(this@MainActivity)
            
            true
          }
          popupMenu.show()
        }
      })
    }
  }
  
  private fun setupAdapter() {
    adapter = TabAdapter(this)
    viewPager.adapter = adapter
    
    TabLayoutMediator(tabLayout, viewPager) { tab, position ->
      tab.text = tabViewModel.fragmentTitles[position]
    }.attach()
    
  }
  
  fun openDrawer(v: View?) {
    binding.drawerLayout.open()
  }
}
