package com.rk.activities.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import com.rk.filetree.saveProjects
import com.rk.file.FileManager
import com.rk.file.FilePermission
import com.rk.file.toFileObject
import com.rk.settings.Settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModels()

    val fileManager = FileManager(this)

    //suspend (isForeground) -> Unit
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
            SessionManager.saveSession(viewModel.tabs.toList(), viewModel.currentTabIndex)
            saveProjects()
            foregroundListener.values.forEach { it.invoke(false) }
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

        setContent {
//            Box(
//                modifier = Modifier
//                    .graphicsLayer(
//                        scaleX = 0.5f,
//                        scaleY = 0.5f,
//                        transformOrigin = TransformOrigin.Center
//                    )
//                    .fillMaxSize()
//            ) {
//
//            }
            MainContentHost()
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