package com.rk.activities.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.rk.file.FileManager
import com.rk.resources.strings
import com.rk.theme.XedTheme
import com.rk.utils.toast
import java.lang.ref.WeakReference

var settingsNavController = WeakReference<NavController?>(null)

class SettingsActivity : AppCompatActivity() {
    val fileManager = FileManager(this)

    companion object {
        private var activityRef = WeakReference<SettingsActivity?>(null)
        var instance: SettingsActivity?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()
        setContent {
            XedTheme {

                    Surface {
                        val navController = rememberNavController()
                        settingsNavController = WeakReference(navController)
                        SettingsNavHost(activity = this@SettingsActivity, navController = navController)
                        if (intent.hasExtra("route")) {
                            val route = intent.getStringExtra("route")
                            if (route != null) {
                                navController.navigate(route)
                            } else {
                                toast(strings.unknown_err)
                            }
                        }
                    }

            }
        }
    }
}
