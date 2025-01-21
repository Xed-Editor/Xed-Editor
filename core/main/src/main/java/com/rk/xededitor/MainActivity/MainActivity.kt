package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.extension.ExtensionManager
import com.rk.file.FileObject
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.application
import com.rk.libcommons.editor.ControlPanel
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.toast
import com.rk.libcommons.toastIt
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.MainActivity.file.TabSelectedListener
import com.rk.xededitor.MainActivity.handlers.MenuClickHandler
import com.rk.xededitor.MainActivity.handlers.PermissionHandler
import com.rk.xededitor.MainActivity.handlers.updateMenu
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivityTabBinding
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.ref.WeakReference


class MainActivity : BaseActivity() {

    companion object {
        var activityRef = WeakReference<MainActivity?>(null)
        fun withContext(invoke: MainActivity.() -> Unit) {
            activityRef.get()?.let { invoke(it) }
        }
    }

    var binding: ActivityTabBinding? = null
    var viewPager: ViewPager2? = null
    var tabLayout: TabLayout? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    var fileManager: FileManager? = FileManager(this)
    var menu: Menu? = null
    var adapter: TabAdapter? = null
    val tabViewModel: TabViewModel by viewModels()

    class TabViewModelState(
        val fragmentFiles: MutableList<FileObject>,
        val fragmentTypes:  MutableList<FragmentType>,
        val fragmentTitles:  MutableList<String>,
        val fileSet: HashSet<String>
    ) : Serializable

    class TabViewModel : ViewModel() {
        var fragmentFiles = mutableListOf<FileObject>()
        var fragmentTypes = mutableListOf<FragmentType>()
        var fragmentTitles = mutableListOf<String>()
        var fileSet = HashSet<String>()
        private var _isRestoring = false
        val isRestoring:Boolean
            get() = _isRestoring

        @OptIn(DelicateCoroutinesApi::class)
        fun save(){
            val state = toState()
            GlobalScope.launch(Dispatchers.IO) {
                FileOutputStream(File(application!!.cacheDir,"state").also { if (it.exists()){it.delete()} }).use { fileOutputStream ->
                    ObjectOutputStream(fileOutputStream).use {
                        it.writeObject(state)
                    }
                }
            }
        }

        fun restore(){
            viewModelScope.launch(Dispatchers.IO) {
                if (PreferencesData.getBoolean(PreferencesKeys.RESTORE_SESSIONS,false).not()){
                    return@launch
                }
                _isRestoring = true
                val stateFile = File(application!!.cacheDir, "state")
                runCatching {
                    if (stateFile.exists()) {
                        FileInputStream(stateFile).use { fileInputStream ->
                            ObjectInputStream(fileInputStream).use {
                                restoreState(it.readObject() as TabViewModelState)
                            }
                        }
                    }
                }.onFailure {
                    it.printStackTrace()
                    stateFile.delete()
                    toast("State lost")
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        delay(1000)
                        withContext {
                            if (fragmentFiles.isNotEmpty()) {
                                binding!!.tabs.visibility = View.VISIBLE
                                binding!!.mainView.visibility = View.VISIBLE
                                binding!!.openBtn.visibility = View.GONE
                            }

                            TabLayoutMediator(tabLayout!!, viewPager!!) { tab, position ->
                                tab.text = tabViewModel.fragmentTitles[position]
                            }.attach()

                            binding?.viewpager2?.offscreenPageLimit = tabLimit.toInt()
                        }
                    }
                }
                _isRestoring = false
            }
        }

        private fun toState(): TabViewModelState {
            return TabViewModelState(
                fragmentFiles = fragmentFiles,
                fragmentTypes = fragmentTypes,
                fragmentTitles = fragmentTitles,
                fileSet = fileSet
            )
        }

        private fun restoreState(state: TabViewModelState) {
            fragmentFiles = state.fragmentFiles
            fragmentTypes = state.fragmentTypes
            fragmentTitles = state.fragmentTitles
            fileSet = state.fileSet
        }

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
    fun showControlPanel(c: ControlPanel) {
        showControlPanel(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityRef = WeakReference(this)
        binding = ActivityTabBinding.inflate(layoutInflater)

        tabViewModel.restore()

        setContentView(binding!!.root)
        setSupportActionBar(binding!!.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            SetupEditor.initActivity(this@MainActivity, calculateColors = {
                val lightThemeContext = ContextThemeWrapper(
                    this@MainActivity,
                    com.google.android.material.R.style.Theme_Material3_DynamicColors_Light
                )
                val darkThemeContext = ContextThemeWrapper(
                    this@MainActivity,
                    com.google.android.material.R.style.Theme_Material3_DynamicColors_Dark
                )

                val lightSurfaceColor = MaterialColors.getColor(
                    lightThemeContext,
                    com.google.android.material.R.attr.colorSurface,
                    Color.WHITE
                )

                val darkSurfaceColor = MaterialColors.getColor(
                    darkThemeContext,
                    com.google.android.material.R.attr.colorSurface,
                    Color.BLACK
                )

                val lightsurfaceColorHex =
                    String.format("#%06X", 0xFFFFFF and lightSurfaceColor)
                val darksurfaceColorHex =
                    String.format("#%06X", 0xFFFFFF and darkSurfaceColor)

                Pair(darksurfaceColorHex, lightsurfaceColorHex)
            })
        }

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

        ExtensionManager.onAppCreated()
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                updateMenu(adapter?.getCurrentFragment())
            }
        }
    }


    val toolItems = hashSetOf<Int>()

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu

        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }

        menu.findItem(R.id.action_add).isVisible = true

        val tool = ContextCompat.getDrawable(this, drawables.build)
        var order = 0
        Mutators.getMutators().forEach {
            menu.findItem(R.id.tools).subMenu?.add(
                1, it.hashCode(), order, it.name
            )?.icon = tool;order++;toolItems.add(it.hashCode())
        }

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
        tabViewModel.save()

        if (PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false)) {
            kotlin.runCatching { saveAllFiles() }
        }

        super.onPause()
    }

    private fun saveAllFiles() {
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
        ExtensionManager.onAppResumed()
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
            offscreenPageLimit = 1
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
        binding!!.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
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
        })
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
        ExtensionManager.onAppDestroyed()
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
