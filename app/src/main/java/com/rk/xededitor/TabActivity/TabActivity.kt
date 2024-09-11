package com.rk.xededitor.TabActivity


import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.ActionPopup
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.TabActivity.editor.TabFragment
import com.rk.xededitor.TabActivity.file.FileManager
import com.rk.xededitor.TabActivity.file.ProjectManager
import com.rk.xededitor.TabActivity.handlers.MenuClickHandler
import com.rk.xededitor.TabActivity.handlers.MenuItemHandler
import com.rk.xededitor.TabActivity.handlers.PermissionHandler
import com.rk.xededitor.databinding.ActivityTabBinding
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Queue
import java.util.WeakHashMap


class TabActivity : AppCompatActivity() {
  
  companion object{
    var activityRef = WeakReference<TabActivity?>(null)
  }
  
  lateinit var binding: ActivityTabBinding
  private lateinit var viewPager: ViewPager2
  private lateinit var tabLayout: TabLayout
  private lateinit var drawerToggle: ActionBarDrawerToggle
  private lateinit var fm: FileManager
  lateinit var menu:Menu
  private val pausedQueue:Queue<Runnable> = LinkedList()
  
  fun postPausedQueue(runnable: Runnable){
    pausedQueue.add(runnable)
  }
  
  
  
  val fragmentFiles = mutableListOf<File>()
  private val fragmentTitles = mutableListOf<String>()
  
  private val TAB_LIMIT = 50
  
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
    
    ProjectManager.restoreProjects(this)
    
    fm = FileManager(this)
    setupNavigationRail()
    
    savedInstanceState?.let { restoreState(it) }
    
    if (savedInstanceState == null){
      //restore state from disk
    }else{
      restoreState(savedInstanceState)
    }
    
    setupAdapter()
   
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
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
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
    lifecycleScope.launch(Dispatchers.Main){
      while (pausedQueue.isNotEmpty()) {
        delay(100)
        withContext(Dispatchers.Main) {
          pausedQueue.poll()?.run()
        }
      }
    }
    ProjectManager.processQueue(this)
    super.onResume()
  }
  
  @SuppressLint("SetTextI18n")
  @OptIn(DelicateCoroutinesApi::class)
  private fun setupNavigationRail(){
    val openFileId = View.generateViewId()
    val openDirId = View.generateViewId()
    val openPathId = View.generateViewId()
    val privateFilesId = View.generateViewId()
    val cloneRepo = View.generateViewId()
    
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
        cloneRepo -> {
          val view = LayoutInflater.from(this@TabActivity).inflate(R.layout.popup_new, null)
          view.findViewById<LinearLayout>(R.id.mimeTypeEditor).visibility = View.VISIBLE
          val repolinkedit = view.findViewById<EditText>(R.id.name).apply {
            hint = "Repository git link"
          }
          val branchedit = view.findViewById<EditText>(R.id.mime).apply {
            hint = "Branch"
            setText("main")
          }
          MaterialAlertDialogBuilder(this).setTitle("Clone repository")
            .setView(view).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
              val repoLink = repolinkedit.text.toString()
              val branch = branchedit.text.toString()
              val repoName = repoLink.substringAfterLast("/").removeSuffix(".git")
              val repoDir = File(SettingsData.getString(Keys.GIT_REPO_DIR, "/storage/emulated/0") + "/" + repoName)
              if (repoLink.isEmpty() || branch.isEmpty()) {
                rkUtils.toast(this, "Please fill in both fields")
              }
              else if (repoDir.exists()) {
                rkUtils.toast(this, "$repoDir already exists!")
              }
              else {
                val loadingPopup = LoadingPopup(this, null).setMessage("Cloning repository...")
                loadingPopup.show()
                GlobalScope.launch(Dispatchers.IO) {
                  try {
                    Git.cloneRepository().setURI(repoLink).setDirectory(repoDir).setBranch(branch).call()
                    withContext(Dispatchers.Main) {
                      loadingPopup.hide()
                      ProjectManager.addProject(this@TabActivity,repoDir)
                    }
                  }
                  catch (e: Exception) {
                    val credentials = SettingsData.getString(Keys.GIT_CRED, "").split(":")
                    if (credentials.size != 2) {
                      withContext(Dispatchers.Main) {
                        loadingPopup.hide()
                        rkUtils.toast(this@TabActivity, "Repository is private. Check your credentials")
                      }
                    }
                    else {
                      try {
                        Git.cloneRepository().setURI(repoLink).setDirectory(repoDir).setBranch(branch).setCredentialsProvider(
                          UsernamePasswordCredentialsProvider(credentials[0], credentials[1])
                        ).call()
                        withContext(Dispatchers.Main) {
                          loadingPopup.hide()
                          ProjectManager.addProject(this@TabActivity,repoDir)
                        }
                      }
                      catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                          loadingPopup.hide()
                          rkUtils.toast(this@TabActivity, "Error: ${e.message}")
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
      ActionPopup(this).apply {
        addItem("Open a File","Choose a file from storage to directly edit it",
          ContextCompat.getDrawable(this@TabActivity,R.drawable.outline_insert_drive_file_24),listener,openFileId)
        addItem("Open a Directory","Choose a directory from storage as a project",
          ContextCompat.getDrawable(this@TabActivity,R.drawable.outline_folder_24),listener,openDirId)
        addItem("Open from Path","Open a project/file from a path",
          ContextCompat.getDrawable(this@TabActivity,R.drawable.android),listener,openPathId)
        addItem("Clone repository","Clone repository using Git",ContextCompat.getDrawable(this@TabActivity,R.drawable.git),listener,
          cloneRepo
        )
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
      MenuClickHandler.handle(this,item)
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
    SetupEditor.init(this)
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
          MenuItemHandler.update(this@TabActivity)
          tab.text = tab.text
        }
        
        override fun onTabUnselected(tab: Tab?) {
        
        }
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
            MenuItemHandler.update(this@TabActivity)
            
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
    if (fragmentFiles.isNotEmpty()){
      binding.tabs.visibility = View.VISIBLE
      binding.mainView.visibility = View.VISIBLE
      binding.openBtn.visibility = View.GONE
    }
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
    binding.tabs.visibility = View.VISIBLE
    binding.mainView.visibility = View.VISIBLE
    binding.openBtn.visibility = View.GONE
  }
  
  private fun removeFragment(position: Int) {
    if (position >= 0 && position < fragmentFiles.size) {
      fragmentFiles.removeAt(position)
      fragmentTitles.removeAt(position)
      (viewPager.adapter as? FragmentAdapter)?.apply {
        notifyItemRemovedX(position)
      }
      
    }
    if (fragmentFiles.isEmpty()){
      binding.tabs.visibility = View.GONE
      binding.mainView.visibility = View.GONE
      binding.openBtn.visibility = View.VISIBLE
    }
    
  }
  
  @SuppressLint("NotifyDataSetChanged")
  private fun clearAllFragments() {
    fragmentFiles.clear()
    fragmentTitles.clear()
    (viewPager.adapter as? FragmentAdapter)?.notifyDataSetChanged()
    binding.tabs.visibility = View.GONE
    binding.mainView.visibility = View.GONE
    binding.openBtn.visibility = View.VISIBLE
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
  val tabFragments = WeakHashMap<Int, TabFragment>()
  
  fun getCurrentFragment(): TabFragment?{
    return tabFragments[tabLayout.selectedTabPosition]
  }
  
  inner class FragmentAdapter(activity: AppCompatActivity, lifecycle: Lifecycle) :
    FragmentStateAdapter(activity.supportFragmentManager, lifecycle) {
    private val itemIds = mutableMapOf<Int, Long>()
    
    override fun getItemCount(): Int = fragmentFiles.size
    
    override fun createFragment(position: Int): Fragment {
      val file = fragmentFiles[position]
      return TabFragment.newInstance(file).apply { tabFragments[position] = this }
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
