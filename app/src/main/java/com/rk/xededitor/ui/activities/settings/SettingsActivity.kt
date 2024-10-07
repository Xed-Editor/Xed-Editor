package com.rk.xededitor.ui.activities.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent

import androidx.navigation.compose.rememberNavController

import com.rk.xededitor.BaseActivity
import com.rk.xededitor.ui.theme.KarbonTheme

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarbonTheme {
                val navController = rememberNavController()
                SettingsNavHost(navController)
            }
        }
    }
}
