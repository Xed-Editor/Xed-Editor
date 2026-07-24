package com.rk.activities.main

import androidx.compose.runtime.mutableStateListOf
import com.rk.extension.api.DynamicRoute
import com.rk.extension.api.XedExtensionPoint

object MainRouteRegistry {
    private val _routes = mutableStateListOf<DynamicRoute>()
    val routes: List<DynamicRoute>
        get() = _routes.toList()

    @XedExtensionPoint
    fun registerRoute(route: DynamicRoute) {
        _routes.add(route)
    }

    @XedExtensionPoint
    fun unregisterRoute(route: DynamicRoute) {
        _routes.remove(route)
    }
}
