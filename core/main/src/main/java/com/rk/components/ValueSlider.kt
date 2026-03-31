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

@Composable
fun ValueSlider(
    label: String,
    description: String? = null,
    singleLineDescription: Boolean = false,
    min: Int,
    max: Int,
    default: Int,
    useSteps: Boolean = true,
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
                    sliderPosition = it.toInt()
                    job?.cancel()
                    job =
                        scope.launch {
                            delay(debounce)
                            onValueChanged.invoke(it.toInt())
                        }
                },
                steps = if (useSteps) max - min - 1 else 0,
                valueRange = min.toFloat()..max.toFloat(),
            )
        }
    }
}
