package com.rk.settings

import com.rk.extension.api.DynamicRoute
import com.rk.extension.api.XedExtensionPoint
import com.rk.icons.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsCategory(
    val label: String,
    val description: String,
    val icon: Icon,
    val route: String,
)

object SettingsRegistry {

    private val _categories = MutableStateFlow<List<SettingsCategory>>(emptyList())
    val categories: StateFlow<List<SettingsCategory>> = _categories.asStateFlow()

    private val _routes = MutableStateFlow<List<DynamicRoute>>(emptyList())
    val routes: StateFlow<List<DynamicRoute>> = _routes.asStateFlow()

    @XedExtensionPoint
    fun registerCategory(category: SettingsCategory) {
        _categories.update { it + category }
    }

    @XedExtensionPoint
    fun unregisterCategory(category: SettingsCategory) {
        _categories.update { it - category }
    }

    @XedExtensionPoint
    fun registerRoute(route: DynamicRoute) {
        _routes.update { it + route }
    }

    @XedExtensionPoint
    fun unregisterRoute(route: DynamicRoute) {
        _routes.update { it - route }
    }
}
