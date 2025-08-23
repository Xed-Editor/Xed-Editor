package com.rk.xededitor.ui.activities.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.rk.compose.filetree.DrawerContent
import com.rk.compose.filetree.isLoading
import com.rk.compose.filetree.restoreProjects
import com.rk.compose.filetree.saveProjects
import com.rk.extension.LocalExtensionManager
import com.rk.extension.ProvideExtensionManager
import com.rk.extension.internal.loadAllExtensions
import com.rk.file.FileManager
import com.rk.file.FilePermission
import com.rk.file.UriWrapper
import com.rk.libcommons.dialog
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.FPSBooster
import com.rk.xededitor.ui.theme.KarbonTheme
import com.rk.xededitor.ui.theme.amoled
import com.rk.xededitor.ui.theme.currentTheme
import com.rk.xededitor.ui.theme.dynamicTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

@Composable
fun getDrawerWidth(): Dp {
    val configuration = LocalConfiguration.current
    return (configuration.screenWidthDp * 0.83).dp
}

class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModels()
    val fileManager = FileManager(this)


    companion object {
        var isPaused = false
        private var activityRef = WeakReference<MainActivity?>(null)
        var instance: MainActivity?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onPause() {
        isPaused = true
        GlobalScope.launch(Dispatchers.IO) {
            TabCache.saveFileTabs(viewModel.tabs.toList())
        }
        GlobalScope.launch(Dispatchers.IO) {
            saveProjects()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        instance = this
        lifecycleScope.launch {
            handleIntent(intent)
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    suspend fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action) {
            val uri = intent.data!!
            val file = UriWrapper(uri, false)
            viewModel.newTab(file)
            setIntent(Intent())
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(Settings.default_night_mode)
        super.onCreate(savedInstanceState)
        FPSBooster(this)
        FilePermission.verifyStoragePermission(this)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            KarbonTheme {

                ProvideExtensionManager {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                        val scope = rememberCoroutineScope()

                        val extensionManager = LocalExtensionManager.current

                        LaunchedEffect(Unit) {
                            extensionManager.loadAllExtensions()
                        }

                        BackHandler {
                            if (drawerState.isOpen) {
                                scope.launch {
                                    drawerState.close()
                                }
                            }else if (viewModel.tabs.isNotEmpty()){
                                dialog(title = strings.attention.getString(), msg = strings.confirm_exit.getString(), onCancel = {}, onOk = {
                                    finish()
                                }, okString = strings.exit)
                            }else{
                                finish()
                            }

                        }

                        ModalNavigationDrawer(
                            modifier = Modifier
                                .imePadding()
                                .systemBarsPadding(),
                            drawerState = drawerState,
                            gesturesEnabled = drawerState.isOpen,
                            //scrimColor = androidx.compose.ui.graphics.Color.Transparent,
                            drawerContent = {

                                ModalDrawerSheet(
                                    modifier = Modifier.width(getDrawerWidth()),
                                    drawerShape = RectangleShape
                                    //drawerTonalElevation = 0.dp
                                ) {

                                    LaunchedEffect(Unit) {
                                        isLoading = true
                                        restoreProjects()
                                        isLoading = false
                                    }
                                    DrawerContent(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 8.dp),
                                        onFileSelected = { file ->
                                            scope.launch {
                                                if (file.isFile()) {
                                                    viewModel.newTab(file, switchToTab = true)
                                                }

                                                delay(60)
                                                drawerState.close()
                                            }
                                        }
                                    )
                                }
                            }
                        ) {
                            Scaffold(
                                modifier = Modifier.nestedScroll(
                                    rememberNestedScrollInteropConnection()
                                ),
                                topBar = {
                                    XedTopBar(drawerState = drawerState, viewModel = viewModel)
                                }
                            ) { innerPadding ->
                                MainContent(
                                    innerPadding = innerPadding,
                                    drawerState = drawerState,
                                    viewModel = viewModel
                                )
                            }

                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        FilePermission.onRequestPermissionsResult(requestCode, grantResults, lifecycleScope, this)
    }
}