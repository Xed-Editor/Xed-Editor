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
import com.rk.activities.main.navigation.MainRouteRegistry
import com.rk.activities.main.navigation.MainRoutes
import com.rk.activities.main.session.DocumentStateDatabase
import com.rk.activities.main.session.SessionManager
import com.rk.activities.main.ui.DisclaimerScreen
import com.rk.activities.main.ui.MainContentHost
import com.rk.commands.KeybindingsManager
import com.rk.drawer.DrawerPersistence
import com.rk.drawer.DrawerViewModel
import com.rk.extension.api.IntentHandleRegistry
import com.rk.file.FileManager
import com.rk.file.FilePermission
import com.rk.file.toFileObject
import com.rk.lsp.LspRegistry
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.support.handleSupport
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
        lifecycleScope.launch(Dispatchers.IO) {
            handleIntent(intent)
            foregroundListener.values.forEach { it.invoke(true) }

            val lspConfigChanges = LspRegistry.getConfigurationChanges(this@MainActivity)
            if (lspConfigChanges.isNotEmpty()) {
                val affectedExtensions = lspConfigChanges.flatMap { it.supportedExtensions }
                viewModel.editorTabs
                    .filterWithFiles { _, file ->
                        affectedExtensions.contains(file.getExtension())
                    }
                    .forEach { it.applyHighlightingAndConnectLSP() }
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

            if (IntentHandleRegistry.handleIntent(file)) {
                setIntent(Intent())
                return
            }

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

        lifecycleScope.launch(Dispatchers.IO) {
            val db = DocumentStateDatabase.getDatabase(applicationContext)
            val thirtyDaysInMillis = 1000L * 60 * 60 * 24 * 30
            val timestamp = System.currentTimeMillis() - thirtyDaysInMillis
            // TODO: Instead of always deleting when older than 30 days, delete the oldest when limit is reached
            db.documentStateDao().deleteOlderThan(timestamp)
        }

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

                MainRouteRegistry.routes.forEach { customRoute ->
                    composable(customRoute.route, arguments = customRoute.arguments) { backStackEntry ->
                        customRoute.content(navController, backStackEntry)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        FilePermission.onRequestPermissionsResult(requestCode, grantResults, lifecycleScope, this)
    }
}
