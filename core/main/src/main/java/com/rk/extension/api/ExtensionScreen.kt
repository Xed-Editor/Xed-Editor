package com.rk.extension.api

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope

@XedExtensionPoint
abstract class ExtensionScreen {
    /**
     * The [ComponentActivity] that hosts this extension screen. This property provides access to the activity context
     * and is initialized once the screen is attached to the host.
     *
     * @see ExtensionActivity
     * @see isAttached
     */
    lateinit var host: ExtensionActivity
        internal set

    /** True once [host] has been assigned (i.e. onCreate has started). Guards early access. */
    val isAttached: Boolean
        get() = ::host.isInitialized

    /** @see ComponentActivity.onCreate */
    open fun onCreate(savedInstanceState: Bundle?) {}

    /** @see ComponentActivity.onStart */
    open fun onStart() {}

    /** @see ComponentActivity.onResume */
    open fun onResume() {}

    /** @see ComponentActivity.onPause */
    open fun onPause() {}

    /** @see ComponentActivity.onStop */
    open fun onStop() {}

    /** @see ComponentActivity.onRestart */
    open fun onRestart() {}

    /** @see ComponentActivity.onDestroy */
    open fun onDestroy() {}

    /** @see ComponentActivity.onSaveInstanceState */
    open fun onSaveInstanceState(outState: Bundle) {}

    /** @see ComponentActivity.onRestoreInstanceState */
    open fun onRestoreInstanceState(savedInstanceState: Bundle) {}

    /** @see ComponentActivity.onNewIntent */
    open fun onNewIntent(intent: Intent) {}

    /** @see ComponentActivity.onConfigurationChanged */
    open fun onConfigurationChanged(newConfig: Configuration) {}

    /** @see ComponentActivity.onLowMemory */
    open fun onLowMemory() {}

    /** @see ComponentActivity.onTrimMemory */
    open fun onTrimMemory(level: Int) {}

    /** @see ComponentActivity.onWindowFocusChanged */
    open fun onWindowFocusChanged(hasFocus: Boolean) {}

    /** @see ComponentActivity.onUserLeaveHint */
    open fun onUserLeaveHint() {}

    /**
     * @see ComponentActivity.getIntent
     * @see ComponentActivity.setIntent
     */
    var intent: Intent
        get() = host.intent
        set(value) {
            host.intent = value
        }

    /** @see ComponentActivity.lifecycleScope */
    val lifecycleScope
        get() = host.lifecycleScope

    /** @see ComponentActivity.lifecycle */
    val lifecycle: Lifecycle
        get() = host.lifecycle

    /**
     * @see ComponentActivity.getTitle
     * @see ComponentActivity.setTitle
     */
    var title: CharSequence
        get() = host.title
        set(value) {
            host.title = value
        }

    /** @see ComponentActivity.finish */
    fun finish() = host.finish()

    /** @see ComponentActivity.setResult */
    fun setResult(resultCode: Int, data: Intent? = null) = host.setResult(resultCode, data)

    /** @see ComponentActivity.setContent */
    fun setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {
        host.setContent(parent, content)
    }

    /** @see ComponentActivity.registerForActivityResult */
    fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> = host.registerForActivityResult(contract, callback)
}
