package com.rk.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceTemplate

@Composable
fun ValueSlider(
    label: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    min: Int,
    max: Int,
    default: Float,
    useSteps: Boolean = true,
    onValueChanged: (Int) -> Unit,
) {
    var sliderPosition by remember { mutableFloatStateOf(default) }

    Column {
        PreferenceTemplate(
            title = label,
            description = description,
            applyPaddings = false,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            Text(sliderPosition.toInt().toString())
        }

        PreferenceTemplate(title = {}) {
            Slider(
                value = sliderPosition,
                onValueChange = {
                    sliderPosition = it
                    onValueChanged.invoke(it.toInt())
                },
                steps = if (useSteps) max - min - 1 else 0,
                valueRange = min.toFloat()..max.toFloat(),
            )
        }
    }
}
