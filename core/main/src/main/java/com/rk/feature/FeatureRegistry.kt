package com.rk.feature

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.utils.application
import com.rk.utils.dialogRes
import com.rk.xededitor.BuildConfig

interface Feature {
    fun init(application: Application)

    fun dispose(application: Application) {}

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
        FeatureRegistry.onToggleChange(key, enable)
    }
}

object FeatureRegistry {
    private val features = mutableMapOf<String, Feature>()
    private val featuresWithoutToggles = mutableListOf<Feature>()
    val toggles = mutableStateListOf<FeatureToggle>()

    init {
        registerToggle(
            FeatureToggle(
                nameRes = strings.debug_options,
                key = "debug_mode",
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
        val toggle = feature.toggle
        if (toggle != null) {
            features[toggle.key] = feature
            registerToggle(toggle)
        } else {
            featuresWithoutToggles.add(feature)
        }
    }

    fun initFeatures(application: Application) {
        featuresWithoutToggles.forEach { it.init(application) }
        features.forEach { (key, feature) ->
            if (isEnabled(key)) {
                feature.init(application)
            }
        }
    }

    fun onToggleChange(key: String, enabled: Boolean) {
        val application = application ?: return
        val feature = features[key] ?: return
        if (enabled) {
            feature.init(application)
        } else {
            feature.dispose(application)
        }
    }

    fun registerToggle(toggle: FeatureToggle) {
        if (toggles.any { it.key == toggle.key }) return
        toggles.add(toggle)
    }

    fun isEnabled(key: String): Boolean {
        return toggles.find { it.key == key }?.state?.value ?: false
    }
}
