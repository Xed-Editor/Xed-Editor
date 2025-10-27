package com.rk.activities.main

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.filetree.DrawerContent
import com.rk.filetree.isLoading
import com.rk.filetree.restoreProjects
import com.rk.filetree.saveProjects
import com.rk.file.FileManager
import com.rk.file.FilePermission
import com.rk.file.toFileObject
import com.rk.utils.dialog
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.XedTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference





class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModels()

    val fileManager = FileManager(this)

    //suspend (isForeground)-> Boolean
    val foregroundListener = hashMapOf<Any,suspend (Boolean)-> Unit>()


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
        GlobalScope.launch {
            foregroundListener.values.forEach { it.invoke(false) }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        instance = this
        lifecycleScope.launch {
            foregroundListener.values.forEach { it.invoke(true) }
        }
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
            val file = uri.toFileObject(expectedIsFile = true)
            viewModel.newTab(file, switchToTab = true)
            setIntent(Intent())
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(Settings.default_night_mode)
        super.onCreate(savedInstanceState)
        FilePermission.verifyStoragePermission(this)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent { MainContentHost() }
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