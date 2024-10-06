package com.rk.xededitor.ui.activities

import android.os.Bundle

import androidx.activity.compose.setContent

import com.rk.xededitor.R
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.ui.theme.KarbonTheme
import com.rk.xededitor.ui.screens.settings.SettingsScreen

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KarbonTheme {
               SettingsScreen()
            }
        }
    }
}