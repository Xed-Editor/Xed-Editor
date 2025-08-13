package com.rk.xededitor.ui.activities.main

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.SurfaceControl
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.compose.filetree.DrawerContent
import com.rk.compose.filetree.isLoading
import com.rk.compose.filetree.restoreProjects
import com.rk.compose.filetree.saveProjects
import com.rk.resources.strings
import com.rk.xededitor.ui.FPSBooster
import com.rk.xededitor.ui.components.GlobalActions
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import kotlin.times

@Composable
fun getDrawerWidth(): Dp {
    val configuration = LocalConfiguration.current
    return (configuration.screenWidthDp * 0.83).dp
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()


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
        GlobalScope.launch(Dispatchers.IO){
            TabCache.saveFileTabs(viewModel.tabs.toList())
        }
        GlobalScope.launch(Dispatchers.IO){
            saveProjects()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        instance = this
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FPSBooster(this)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            KarbonTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

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
                                                viewModel.newEditorTab(file)
                                            }

                                            delay(60)
                                            drawerState.close()
                                        }
                                    }
                                )
                            }
                        }
                    ) {
                        Scaffold(modifier = Modifier.nestedScroll(
                            rememberNestedScrollInteropConnection()
                        ),
                            topBar = {
                                XedTopBar(drawerState = drawerState, viewModel = viewModel)
                            }
                        ) { innerPadding ->
                            MainContent(innerPadding = innerPadding, drawerState = drawerState, viewModel = viewModel)
                        }

                    }
                }
            }
        }
    }
}