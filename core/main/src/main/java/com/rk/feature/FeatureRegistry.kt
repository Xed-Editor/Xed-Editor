package com.rk.feature

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.utils.application
import com.rk.utils.dialogRes
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface Feature {
    fun init(application: Application)

    fun dispose(application: Application) {}

    val toggle: FeatureToggle?
        get() = null
}

data class FeatureToggle(
    val name: String,
    val key: String,
    val default: Boolean,
    val icon: Icon,
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
    private val _toggles = MutableStateFlow<List<FeatureToggle>>(emptyList())
    val toggles: StateFlow<List<FeatureToggle>> = _toggles.asStateFlow()

    init {
        registerToggle(
            FeatureToggle(
                name = strings.debug_options.getString(),
                key = "debug_mode",
                default = BuildConfig.DEBUG,
                icon = Icon.ResourceIcon(drawables.build),
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
        if (_toggles.value.any { it.key == toggle.key }) return
        _toggles.update { it + toggle }
    }

    fun isEnabled(key: String): Boolean {
        return _toggles.value.find { it.key == key }?.state?.value ?: false
    }
}
