package com.rk.color

import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.icons.Error
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings

private typealias AndroidColor = android.graphics.Color

@Composable
fun ColorPicker(
    modifier: Modifier = Modifier,
    initialColor: Color = Color.Black,
    initialFormat: ColorFormat = ColorFormat.HEX,
    onApply: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var `val` by remember { mutableFloatStateOf(0f) }

    val color = Color.hsv(hue, sat, `val`)

    var selectedColorFormat by remember { mutableStateOf(initialFormat) }
    var colorValue by remember { mutableStateOf(selectedColorFormat.getString(color)) }
    var colorError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]
        sat = hsv[1]
        `val` = hsv[2]

        colorValue = selectedColorFormat.getString(initialColor)
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(strings.color_picker)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SatValPanel(hue, sat, `val`, hidePicker = colorError != null) { newSat, newVal ->
                    sat = newSat
                    `val` = newVal
                    val newColor = Color.hsv(hue, sat, `val`)
                    colorValue = selectedColorFormat.getString(newColor)
                    colorError = null
                }

                HueBar(hue, hidePicker = colorError != null) { newHue ->
                    hue = newHue
                    val newColor = Color.hsv(hue, sat, `val`)
                    colorValue = selectedColorFormat.getString(newColor)
                    colorError = null
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ColorFormat.availableTypes.forEach { colorType ->
                        FilterChip(
                            onClick = {
                                selectedColorFormat = colorType
                                colorValue = selectedColorFormat.getString(color)
                            },
                            label = { Text(colorType.label) },
                            selected = selectedColorFormat == colorType,
                            leadingIcon =
                                if (selectedColorFormat == colorType) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Done,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                                        )
                                    }
                                } else null,
                        )
                    }
                }

                TextField(
                    value = colorValue,
                    isError = colorError != null,
                    supportingText =
                        colorError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
                        },
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = {
                        colorValue = it
                        val parsedColor = selectedColorFormat.getColor(it)
                        if (parsedColor != null) {
                            val hsv = FloatArray(3)
                            AndroidColor.colorToHSV(parsedColor.toArgb(), hsv)

                            hue = hsv[0]
                            sat = hsv[1]
                            `val` = hsv[2]

                            colorError = null
                        } else {
                            colorError = strings.invalid_color.getString()
                        }
                    },
                    maxLines = 1,
                    label = { Text(selectedColorFormat.label) },
                    trailingIcon = {
                        colorError?.let {
                            Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error)
                        } ?: Box(Modifier.size(16.dp).background(color))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = colorError == null,
                onClick = {
                    onApply(colorValue)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(strings.ok))
            }
        },
        dismissButton = { TextButton(onDismissRequest) { Text(stringResource(strings.cancel)) } },
    )
}

@Composable
fun SatValPanel(
    hue: Float,
    sat: Float = 0f,
    `val`: Float = 0f,
    hidePicker: Boolean = false,
    setSatVal: (Float, Float) -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    val pressOffset = Offset(sat * widthPx, (1 - `val`) * heightPx)

    Canvas(
        modifier =
            Modifier.size(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .onSizeChanged {
                    widthPx = it.width
                    heightPx = it.height
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val offsetX = offset.x.coerceIn(0f, widthPx.toFloat())
                        val offsetY = offset.y.coerceIn(0f, heightPx.toFloat())
                        setSatVal(offsetX / widthPx, 1 - (offsetY / heightPx))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { input, _ ->
                        val offsetX = input.position.x.coerceIn(0f, widthPx.toFloat())
                        val offsetY = input.position.y.coerceIn(0f, heightPx.toFloat())
                        setSatVal(offsetX / widthPx, 1 - (offsetY / heightPx))
                    }
                }
    ) {
        val width = size.width
        val height = size.height

        val hueColor = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))

        drawRoundRect(
            object : ShaderBrush() {
                override fun createShader(size: Size): Shader {
                    val tileMode = Shader.TileMode.CLAMP
                    val satShader = LinearGradient(0f, 0f, width, 0f, Color.White.toArgb(), hueColor, tileMode)
                    val valShader = LinearGradient(0f, 0f, 0f, height, AndroidColor.WHITE, AndroidColor.BLACK, tileMode)

                    return ComposeShader(satShader, valShader, PorterDuff.Mode.MULTIPLY)
                }
            }
        )

        if (!hidePicker) {
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = pressOffset,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
fun HueBar(hue: Float = 0f, hidePicker: Boolean = false, setHue: (Float) -> Unit) {
    var widthPx by remember { mutableIntStateOf(0) }
    val pressOffset = Offset((hue / 360f) * widthPx, 0f)

    Box(modifier = Modifier.height(20.dp).fillMaxWidth()) {
        Canvas(
            modifier =
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(50))
                    .onSizeChanged { widthPx = it.width }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val offsetX = offset.x.coerceIn(0f, widthPx.toFloat())
                            setHue((offsetX / widthPx) * 360f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { input, _ ->
                            val offsetX = input.position.x.coerceIn(0f, widthPx.toFloat())
                            setHue((offsetX / widthPx) * 360f)
                        }
                    }
        ) {
            val width = size.width
            val height = size.height

            val hueStep = 360 / width
            for (i in 0 until width.toInt()) {
                val hue = i * hueStep
                val color = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
                drawLine(color = Color(color), start = Offset(i.toFloat(), 0f), end = Offset(i.toFloat(), height))
            }

            if (!hidePicker) {
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = pressOffset.copy(y = height / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
