package com.rk.activities.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rk.commands.KeybindingsManager
import com.rk.drawer.DrawerPersistence
import com.rk.drawer.DrawerViewModel
import com.rk.file.FileManager
import com.rk.file.FilePermission
import com.rk.file.toFileObject
import com.rk.lsp.LspRegistry
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.support.handleSupport
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.applyHighlightingAndConnectLSP
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModels()
    val drawerViewModel: DrawerViewModel by viewModels()
    val fileManager = FileManager(this)

    // suspend (isForeground) -> Unit
    val foregroundListener = hashMapOf<Any, suspend (Boolean) -> Unit>()

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
            SessionManager.saveSession(viewModel.tabs, viewModel.currentTabIndex)
            DrawerPersistence.saveState(drawerViewModel)
            foregroundListener.values.forEach { it.invoke(false) }

            LspRegistry.updateConfiguration(this@MainActivity)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        instance = this
        // Re-check storage permission after returning from system settings so granting it is
        // picked up immediately (and storage-gated features unlock) without restarting the app.
        if (Settings.shown_disclaimer) {
            lifecycleScope.launch(Dispatchers.Main) { FilePermission.verifyStoragePermission(this@MainActivity) }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            handleIntent(intent)
            foregroundListener.values.forEach { it.invoke(true) }

            val lspConfigChanges = LspRegistry.getConfigurationChanges(this@MainActivity)
            if (lspConfigChanges.isNotEmpty()) {
                val affectedExtensions = lspConfigChanges.flatMap { it.supportedExtensions }
                viewModel.tabs
                    .filterIsInstance<EditorTab>()
                    .filter { affectedExtensions.contains(it.file.getExtension()) }
                    .forEach { tab -> tab.applyHighlightingAndConnectLSP() }
            }

            delay(1000.milliseconds)
            handleSupport()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    suspend fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action) {
            if (intent.data == null) {
                errorDialog(msg = strings.invalid_intent.getFilledString(intent.toString()))
                return
            }

            val uri = intent.data!!

            if (uri.toString().startsWith("content://telephony")) {
                toast(strings.unsupported_content)
                return
            }

            val file = uri.toFileObject(expectedIsFile = true)

            viewModel.awaitSessionRestoration()
            viewModel.editorManager.openFile(file, projectRoot = null, switchToTab = true)
            setIntent(Intent())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handledEvent = KeybindingsManager.handleGlobalEvent(event, this)
        if (handledEvent) return true
        return super.dispatchKeyEvent(event)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(Settings.theme_mode)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val navController = rememberNavController()
            val startDestination = remember {
                if (Settings.shown_disclaimer) {
                    MainRoutes.Main.route
                } else {
                    MainRoutes.Disclaimer.route
                }
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(MainRoutes.Main.route) {
                    MainContentHost()
                    LaunchedEffect(Unit) {
                        FilePermission.verifyStoragePermission(this@MainActivity)
                    }
                }
                composable(MainRoutes.Disclaimer.route) { DisclaimerScreen(navController) { finishAffinity() } }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        FilePermission.onRequestPermissionsResult(requestCode, grantResults, lifecycleScope, this)
    }
}
