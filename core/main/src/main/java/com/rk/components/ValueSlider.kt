package com.rk.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SmoothValueSlider(
    label: String,
    description: String? = null,
    singleLineDescription: Boolean = false,
    min: Int,
    max: Int,
    default: Int,
    debounce: Long = 300,
    onValueChanged: (Int) -> Unit,
) =
    ValueSliderImpl(
        label = label,
        description = description,
        singleLineDescription = singleLineDescription,
        min = min,
        max = max,
        default = default,
        steps = 0,
        valueMapper = { it.toInt() },
        debounce = debounce,
        onValueChanged = onValueChanged,
    )

@Composable
fun SteppedValueSlider(
    label: String,
    description: String? = null,
    singleLineDescription: Boolean = false,
    min: Int,
    max: Int,
    default: Int,
    stepSize: Int = 1,
    debounce: Long = 300,
    onValueChanged: (Int) -> Unit,
) =
    ValueSliderImpl(
        label = label,
        description = description,
        singleLineDescription = singleLineDescription,
        min = min,
        max = max,
        default = default,
        steps = ((max - min) / stepSize).coerceAtLeast(1) - 1,
        valueMapper = { it.toInt() },
        debounce = debounce,
        onValueChanged = onValueChanged,
    )

@Composable
fun RoundedValueSlider(
    label: String,
    description: String? = null,
    singleLineDescription: Boolean = false,
    min: Int,
    max: Int,
    default: Int,
    stepSize: Int,
    debounce: Long = 300,
    onValueChanged: (Int) -> Unit,
) {
    val sliderMin = roundedSliderMin(min)

    ValueSliderImpl(
        label = label,
        description = description,
        singleLineDescription = singleLineDescription,
        min = sliderMin,
        max = max,
        default = default.coerceAtLeast(min),
        steps = ((max - sliderMin) / stepSize).coerceAtLeast(1) - 1,
        valueMapper = { value ->
            value.toInt().coerceAtLeast(min)
        },
        debounce = debounce,
        onValueChanged = onValueChanged,
    )
}

private fun roundedSliderMin(min: Int): Int {
    if (min <= 0) return 0

    var magnitude = 1
    while (magnitude <= min / 10) {
        magnitude *= 10
    }

    val rounded = (min / magnitude) * magnitude
    return if (rounded == magnitude) 0 else rounded
}

@Composable
private fun ValueSliderImpl(
    label: String,
    description: String? = null,
    singleLineDescription: Boolean = false,
    min: Int,
    max: Int,
    default: Int,
    steps: Int,
    valueMapper: (Float) -> Int,
    debounce: Long = 300,
    onValueChanged: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var sliderPosition by remember { mutableIntStateOf(default) }

    Column {
        PreferenceTemplate(
            title = { Text(fontWeight = FontWeight.Bold, text = label) },
            description = {
                description?.let {
                    Text(
                        text = it,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = if (singleLineDescription) 1 else Int.MAX_VALUE,
                    )
                }
            },
            applyPaddings = false,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            Text(sliderPosition.toString(), modifier = Modifier.padding(start = 16.dp), fontWeight = FontWeight.Bold)
        }

        PreferenceTemplate(title = {}) {
            Slider(
                value = sliderPosition.toFloat(),
                onValueChange = {
                    sliderPosition = valueMapper(it)

                    job?.cancel()
                    job = scope.launch {
                        delay(debounce.milliseconds)
                        onValueChanged(sliderPosition)
                    }
                },
                steps = steps,
                valueRange = min.toFloat()..max.toFloat(),
            )
        }
    }
}
