package com.rk.xededitor.ui.components

import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.settings.Settings

@Composable
fun ValueSlider(modifier: Modifier = Modifier, label:@Composable ()-> Unit, min: Int, max: Int, onValueChanged:(Int)-> Unit) {
    val context = LocalContext.current

    var sliderPosition by remember { mutableFloatStateOf(Settings.terminal_font_size.toFloat()) }
    PreferenceGroup {
        PreferenceTemplate(title = label){
            Text(sliderPosition.toInt().toString())
        }
        PreferenceTemplate(title = {}){
            Slider(
                value = sliderPosition,
                onValueChange = {
                    sliderPosition = it
                    onValueChanged.invoke(it.toInt())
                },
                steps = (max - min).toInt() - 1,
                valueRange = min.toFloat()..max.toFloat(),
            )
        }
    }
}