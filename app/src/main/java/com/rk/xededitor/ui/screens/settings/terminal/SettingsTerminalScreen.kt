package com.rk.xededitor.ui.screens.settings.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController

import com.rk.xededitor.R

import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout

@Composable
fun SettingsTerminalScreen() {
    PreferenceLayout(
        label = stringResource(id = R.string.terminal),
        backArrowVisible = true,
    ) {
        
    }
}