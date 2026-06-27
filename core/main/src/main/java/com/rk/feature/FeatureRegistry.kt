package com.rk.feature

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation.NavController

interface Feature {
    fun init(application: Application)
}

object FeatureRegistry {
    private val features = mutableListOf<Feature>()

    fun register(feature: Feature) {
        features.add(feature)
    }

    fun initFeatures(application: Application) {
        features.forEach { it.init(application) }
    }
}

data class SettingsCategory(
    val labelRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val route: String
)

data class SettingsRoute(
    val route: String,
    val content: @Composable (NavController) -> Unit
)

object SettingsRegistry {
    val categories = mutableStateListOf<SettingsCategory>()
    val routes = mutableStateListOf<SettingsRoute>()

    fun registerCategory(category: SettingsCategory) {
        categories.add(category)
    }

    fun registerRoute(route: SettingsRoute) {
        routes.add(route)
    }
}
