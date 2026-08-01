package com.rk.extension.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity

class ExtensionActivity : ComponentActivity() {
    private var screen: ExtensionScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_SCREEN_ID)
        val screen = id?.let { ExtensionActivityRegistry.getScreen(it) }
        this.screen = screen

        if (screen == null) {
            finish()
            return
        }

        screen.host = this
        screen.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        screen?.onNewIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        screen?.onStart()
    }

    override fun onResume() {
        super.onResume()
        screen?.onResume()
    }

    override fun onPause() {
        screen?.onPause()
        super.onPause()
    }

    override fun onStop() {
        screen?.onStop()
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        screen?.onRestart()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        screen?.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        screen?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screen?.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        screen?.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        screen?.onTrimMemory(level)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        screen?.onWindowFocusChanged(hasFocus)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        screen?.onUserLeaveHint()
    }

    override fun onDestroy() {
        screen?.onDestroy()
        intent.getStringExtra(EXTRA_SCREEN_ID)?.let(ExtensionActivityRegistry::unregister)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SCREEN_ID = "extension_screen_id"

        fun start(context: Context, screen: ExtensionScreen) {
            val id = ExtensionActivityRegistry.register(screen)
            val intent = Intent(context, ExtensionActivity::class.java).putExtra(EXTRA_SCREEN_ID, id)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
