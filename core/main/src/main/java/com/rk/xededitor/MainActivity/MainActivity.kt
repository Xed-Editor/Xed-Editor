package com.rk.xededitor.MainActivity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.rk.compose.filetree.DrawerContent
import com.rk.extension.ExtensionManager
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.UI
import com.rk.libcommons.application
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toFileObject
import com.rk.libcommons.toast
import com.rk.libcommons.toastCatching
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.mutator_engine.Engine
import com.rk.runner.Runner
import com.rk.settings.Settings
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.MainActivity.file.TabSelectedListener
import com.rk.xededitor.MainActivity.file.getFragmentType
import com.rk.xededitor.MainActivity.handlers.MenuClickHandler
import com.rk.xededitor.MainActivity.handlers.PermissionHandler
import com.rk.xededitor.MainActivity.handlers.updateMenu
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.saveAllFiles
import com.rk.xededitor.R
import com.rk.xededitor.databinding.ActivityTabBinding
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures
import com.rk.xededitor.ui.screens.settings.mutators.ImplAPI
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import com.rk.xededitor.ui.theme.KarbonTheme
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val fragmentFiles: MutableList<FileObject>
    ) : Serializable

    class TabViewModel : ViewModel() {
        var fragmentFiles = mutableListOf<FileObject>()
        var fragmentTypes = mutableListOf<FragmentType>()
        var fragmentTitles = mutableListOf<String>()
        var fileSet = HashSet<String>()
        var fragmentContent = hashMapOf<String,Content?>()

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
                if (Settings.restore_session.not()){
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
                    toast("State lost")
                }.onSuccess {
                    activityRef.get()?.let {
                        while(it.binding == null || it.tabLayout == null){
                            delay(50)
                        }
                    }

                    UI {
                        withContext {
                            if (fragmentFiles.isNotEmpty()) {
                                binding!!.tabs.visibility = View.VISIBLE
                                binding!!.mainView.visibility = View.VISIBLE
                                binding!!.openBtn.visibility = View.GONE
                            }
                            binding?.viewpager2?.offscreenPageLimit = tabLimit.toInt()

                            lifecycleScope.launch(Dispatchers.Main){
                                TabLayoutMediator(tabLayout!!, viewPager!!) { tab, position ->
                                    tab.text = tabViewModel.fragmentTitles[position]
                                }.attach()
                            }
                        }
                    }
                }
                _isRestoring = false
            }
        }

        private fun toState(): TabViewModelState {
            val files = fragmentFiles //.filter { it !is UriWrapper }.toMutableList()

            return TabViewModelState(
                fragmentFiles = files,
            )
        }

        private fun restoreState(state: TabViewModelState) {
            val files = state.fragmentFiles.filter { it.exists() && it.canRead() && it.isFile() }.toMutableList()
            val types = files.map { it.getFragmentType() }.toMutableList()
            val titles = files.map { it.getName() }.toMutableList()
            val fileSet = files.map { it.getCanonicalPath() }.toHashSet()

            fragmentFiles = files
            fragmentTypes = types
            fragmentTitles = titles
            this.fileSet = fileSet
        }

    }

    var badge: BadgeDrawable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityRef = WeakReference(this)
        tabViewModel.restore()
        binding = ActivityTabBinding.inflate(layoutInflater)
        badge = BadgeDrawable.create(this)
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

        if (tabViewModel.fragmentFiles.isNotEmpty()) {
            binding!!.tabs.visibility = View.VISIBLE
            binding!!.mainView.visibility = View.VISIBLE
            binding!!.openBtn.visibility = View.GONE
        }

        ExtensionManager.onMainActivityCreated()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding != null && binding!!.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding?.drawerLayout?.close()
                } else {
                    // Disable the callback and call super back press behavior
                    this.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    this.isEnabled = true
                }
            }
        })

        lifecycleScope.launch {
            while (true) {
                delay(1500)
                updateMenu(adapter?.getCurrentFragment())
            }
        }

        binding!!.drawerCompose.let {
            it.setContent {
                KarbonTheme {
                    DrawerContent(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }


    val toolItems = hashSetOf<Int>()

    @androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu

        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }

        menu.findItem(R.id.select_highlighting).subMenu?.apply {
            var order = 0
            val list = textmateSources.values.toSet().toMutableList()
            list.sort()
            list.forEach { sourceName ->
                var ext = sourceName.substringAfterLast(".")
                if (sourceName == "text.html.basic"){
                    ext = "html"
                }

                add(1,sourceName.hashCode(),order,ext).setOnMenuItemClickListener {
                    (adapter?.getCurrentFragment()?.fragment as? EditorFragment)?.apply { scope.launch { setupEditor?.setLanguage(sourceName) } }
                    false }

                order++
            }
        }

        menu.findItem(R.id.action_add).isVisible = true
        menu.findItem(R.id.terminal).isVisible = InbuiltFeatures.terminal.state.value
        menu.findItem(R.id.tools).isVisible = InbuiltFeatures.mutators.state.value

        val tool = ContextCompat.getDrawable(this, drawables.build)
        if (InbuiltFeatures.mutators.state.value){
            var order = 0
            Mutators.getMutators().forEach { mut ->
                menu.findItem(R.id.tools).subMenu?.add(
                    1, mut.hashCode(), order, mut.name
                )?.apply { icon = tool;order++;toolItems.add(mut.hashCode())
                    setOnMenuItemClickListener {
                        DefaultScope.launch {
                            Engine(mut.script, DefaultScope).start(onResult = { engine, result ->
                                println(result)
                            }, onError = { t ->
                                t.printStackTrace()
                                errorDialog(t)
                            }, api = ImplAPI::class.java)
                        }
                        false
                    }


                }
            }
        }


        return true
    }

    private fun openTabForIntent(intent: Intent){
        if ((Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action)){
            val uri = intent.data!!
            val fileObject = uri.toFileObject()
            adapter?.addFragment(fileObject)
            setIntent(Intent())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent);
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

        if (Settings.auto_save) {
            toastCatching { saveAllFiles() }
        }

        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        ExtensionManager.onMainActivityResumed()
        PermissionHandler.verifyStoragePermission(this)
        openTabForIntent(intent)
        binding?.viewpager2?.offscreenPageLimit = tabLimit.toInt()
        lifecycleScope.launch{ Runner.onMainActivityResumed() }
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
            lifecycleScope.launch {
                MenuClickHandler.handle(this@MainActivity, item)
            }
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
        if (Settings.auto_save) {
            toastCatching { saveAllFiles() }
        }
        ExtensionManager.onMainActivityDestroyed()
        super.onDestroy()
        binding = null
        adapter = null
        viewPager = null
        fileManager = null
        menu = null
        activityRef = WeakReference(null)
    }
}
