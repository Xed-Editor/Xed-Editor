package com.rk.xededitor.ui.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.compose.rememberNavController
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.ui.theme.KarbonTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
            window.decorView.setBackgroundColor(backgroundColor)
            KarbonTheme {
                val navController = rememberNavController()
                SettingsNavHost(activity = this@SettingsActivity, navController = navController)
            }
        }
    }
}
