package com.rk.settings

import androidx.compose.runtime.mutableStateListOf
import com.rk.extension.api.DynamicRoute
import com.rk.extension.api.XedExtensionPoint
import com.rk.icons.Icon

data class SettingsCategory(
    val label: String,
    val description: String,
    val icon: Icon,
    val route: String,
)

object SettingsRegistry {

    private val _categories = mutableStateListOf<SettingsCategory>()
    val categories: List<SettingsCategory>
        get() = _categories.toList()

    private val _routes = mutableStateListOf<DynamicRoute>()
    val routes: List<DynamicRoute>
        get() = _routes.toList()

    @XedExtensionPoint
    fun registerCategory(category: SettingsCategory) {
        _categories.add(category)
    }

    @XedExtensionPoint
    fun unregisterCategory(category: SettingsCategory) {
        _categories.remove(category)
    }

    @XedExtensionPoint
    fun registerRoute(route: DynamicRoute) {
        _routes.add(route)
    }

    @XedExtensionPoint
    fun unregisterRoute(route: DynamicRoute) {
        _routes.remove(route)
    }
}
