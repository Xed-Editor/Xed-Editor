package com.rk.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.rk.extension.XedExtensionPoint

@XedExtensionPoint
object SettingsRegistry {
    private val screens = mutableMapOf<String, @Composable (NavController) -> Unit>()

    @XedExtensionPoint
    fun registerScreen(route: String, content: @Composable (NavController) -> Unit) {
        screens[route] = content
    }

    @XedExtensionPoint
    fun getScreen(route: String): (@Composable (NavController) -> Unit)? {
        return screens[route]
    }
}
