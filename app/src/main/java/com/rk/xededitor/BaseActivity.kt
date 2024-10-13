package com.rk.xededitor

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.ArrayMap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.rk.plugin.server.api.PluginLifeCycle
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.handlers.KeyEventHandler
import com.rk.xededitor.SimpleEditor.SimpleEditor
import com.rk.xededitor.ui.theme.ThemeManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

@Keep
abstract class BaseActivity : AppCompatActivity() {
    companion object {
        val activityMap = ArrayMap<Class<out BaseActivity>, WeakReference<Activity>>()

        // used by plugins
        @Keep
        fun getActivity(clazz: Class<out BaseActivity>): Activity? {
            return activityMap[clazz]?.get()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            if (this::class.java.name == MainActivity::class.java.name){
                KeyEventHandler.onAppKeyEvent(event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //its completely fine to call initPref multiple times
        Settings.initPref(this, this)
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        activityMap[javaClass] = WeakReference(this)
        PluginLifeCycle.onActivityEvent(this, PluginLifeCycle.LifeCycleType.CREATE)
    }

    override fun onResume() {
        super.onResume()
        PluginLifeCycle.onActivityEvent(this, PluginLifeCycle.LifeCycleType.RESUMED)
    }

    override fun onPause() {
        super.onPause()
        ThemeManager.apply(this)
        PluginLifeCycle.onActivityEvent(this, PluginLifeCycle.LifeCycleType.PAUSED)
    }

    override fun onDestroy() {
        super.onDestroy()
        PluginLifeCycle.onActivityEvent(this, PluginLifeCycle.LifeCycleType.DESTROY)
    }
}
