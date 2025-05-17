package com.rk.xededitor.ui.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.rk.xededitor.ui.screens.settings.terminal.updateTermuxExecStatus
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

var settingsNavController = WeakReference<NavController?>(null)

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarbonTheme {
                Surface {
                    val navController = rememberNavController()
                    settingsNavController = WeakReference(navController)
                    SettingsNavHost(activity = this@SettingsActivity, navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settingsNavController.get()?.let {
            if (it.currentDestination?.route == SettingsRoutes.TerminalSettings.route) {
                lifecycleScope.launch { updateTermuxExecStatus() }
            }
        }
    }
}
