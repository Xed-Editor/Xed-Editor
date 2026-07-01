package com.rk.feature

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavController
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.utils.dialogRes
import com.rk.xededitor.BuildConfig

interface Feature {
    fun init(application: Application)

    val toggle: FeatureToggle?
        get() = null
}

data class FeatureToggle(
    val nameRes: Int,
    val key: String,
    val default: Boolean,
    val iconRes: Int,
    val onSwitch: ((Activity, Boolean, onComplete: (Boolean) -> Unit) -> Unit)? = null,
) {
    val state: MutableState<Boolean> by lazy {
        mutableStateOf(Preference.getBoolean(key, default))
    }

    fun setEnable(enable: Boolean) {
        Preference.setBoolean(key, enable)
        state.value = enable
    }
}

object FeatureRegistry {
    private val features = mutableListOf<Feature>()
    val toggles = mutableStateListOf<FeatureToggle>()

    init {
        registerToggle(
            FeatureToggle(
                nameRes = strings.debug_options,
                key = "debug_mode", // TODO: When checking for debug related settings always check for this to be true
                default = BuildConfig.DEBUG,
                iconRes = drawables.build,
                onSwitch = { activity, checked, onComplete ->
                    if (checked) {
                        dialogRes(
                            activity = activity,
                            title = strings.attention.getString(),
                            msg = strings.debug_mode_warn.getString(),
                            onCancel = { onComplete(false) },
                            onOk = { onComplete(true) },
                        )
                    } else {
                        onComplete(false)
                    }
                },
            )
        )
    }

    fun register(feature: Feature) {
        features.add(feature)
        feature.toggle?.let { registerToggle(it) }
    }

    fun initFeatures(application: Application) {
        features.forEach { feature ->
            val toggle = feature.toggle
            if (toggle == null || isEnabled(toggle.key)) {
                feature.init(application)
            }
        }
    }

    fun registerToggle(toggle: FeatureToggle) {
        toggles.add(toggle)
    }

    fun isEnabled(key: String): Boolean {
        return toggles.find { it.key == key }?.state?.value ?: false
    }
}

data class SettingsCategory(
    val labelRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val route: String,
)

data class SettingsRoute(
    val route: String,
    val content: @Composable (NavController) -> Unit,
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
