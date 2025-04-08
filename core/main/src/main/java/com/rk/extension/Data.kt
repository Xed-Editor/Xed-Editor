package com.rk.extension

import androidx.compose.runtime.Composable

data class SettingsScreen(val label: String, val description: String, val route: String, val icon:@Composable ()-> Unit, val content:@Composable ()-> Unit)