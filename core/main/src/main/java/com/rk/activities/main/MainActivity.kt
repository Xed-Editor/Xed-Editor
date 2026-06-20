package com.rk.activities.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.navigation.compose.*
import com.rk.BaseActivity
import com.rk.commands.KeybindingsManager
import com.rk.file.FileManager
import com.rk.AppDispatchers
import com.rk.AppScope
import kotlinx.coroutines.Dispatchers
import com.rk.file.FilePermission
import com.rk.file.toFileObject
import com.rk.filetree.DrawerPersistence
import com.rk.lsp.LspRegistry
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.safeLaunch
import com.rk.settings.Settings
import com.rk.settings.support.handleSupport
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.applyHighlightingAndConnectLSP
import com.rk.utils.errorDialog
import com.rk.utils.toast
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {
    val viewModel: MainViewModel by viewModels()
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

    override fun onPause() {
        isPaused = true
        AppScope.safeLaunch(AppDispatchers.IO) {
            SessionManager.saveSession(viewModel.tabs, viewModel.currentTabIndex)
            DrawerPersistence.saveState()
            foregroundListener.values.forEach { it.invoke(false) }

            LspRegistry.updateConfiguration(this@MainActivity)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        instance = this
        safeLaunch(AppDispatchers.IO) {
            handleIntent(intent)
            foregroundListener.values.forEach { it.invoke(true) }
            delay(1000)
            handleSupport()

            val lspConfigChanges = LspRegistry.getConfigurationChanges(this@MainActivity)
            if (lspConfigChanges.isNotEmpty()) {
                val affectedExtensions = lspConfigChanges.flatMap { it.supportedExtensions }
                viewModel.tabs
                    .filterIsInstance<EditorTab>()
                    .filter { affectedExtensions.contains(it.file.getExtension()) }
                    .forEach { tab -> tab.applyHighlightingAndConnectLSP() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    suspend fun handleIntent(intent: Intent) {
        if (intent.hasExtra("open_terminal")) {
            val cwd = intent.getStringExtra("cwd")
            withContext(Dispatchers.Main) {
                viewModel.openTerminal(cwd)
            }
            setIntent(Intent())
            return
        }

        if (Intent.ACTION_VIEW == intent.action || Intent.ACTION_EDIT == intent.action) {
            val uri = intent.data
            if (uri == null) {
                errorDialog(strings.invalid_intent.getFilledString(intent.toString()))
                return
            }

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
            NavHost(
                navController = navController,
                startDestination =
                    if (Settings.shown_disclaimer) {
                        MainRoutes.Main.route
                    } else {
                        MainRoutes.Disclaimer.route
                    },
            ) {
                composable(MainRoutes.Main.route) {
                    MainContentHost()
                    LaunchedEffect(Unit) { FilePermission.verifyStoragePermission(this@MainActivity) }
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
