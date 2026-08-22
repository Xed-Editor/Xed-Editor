package com.rk.activities.main.navigation

import com.rk.extension.api.DynamicRoute
import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MainRouteRegistry {
    private val _routes = MutableStateFlow<List<DynamicRoute>>(emptyList())
    val routes: StateFlow<List<DynamicRoute>> = _routes.asStateFlow()

    @XedExtensionPoint
    fun registerRoute(route: DynamicRoute) {
        _routes.update { it + route }
    }

    @XedExtensionPoint
    fun unregisterRoute(route: DynamicRoute) {
        _routes.update { it - route }
    }
}
