package com.rk.components.compose.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

inline fun Modifier.addIf(condition: Boolean, crossinline factory: Modifier.() -> Modifier): Modifier =
    if (condition) factory() else this

inline fun <T> Modifier.addIfNotNull(value: T?, crossinline factory: Modifier.(T) -> Modifier): Modifier =
    if (value != null) factory(value) else this

fun Modifier.holdable(
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    onLongClick: () -> Boolean,
    onClick: () -> Unit = {},
    initialDelayMillis: Long = 500L,
    repeatDelayMillis: Long = 50L,
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    pointerInput(enabled, repeatOnHold) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var held = true
                var longClickTriggered = false
                var repeated = false

                val job = launch {
                    delay(initialDelayMillis.milliseconds)

                    if (held) {
                        longClickTriggered = currentOnLongClick()

                        if (repeatOnHold) {
                            repeated = true
                            while (held) {
                                currentOnClick()
                                delay(repeatDelayMillis.milliseconds)
                            }
                        }
                    }
                }

                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                held = false
                job.cancel()

                if (up != null && !longClickTriggered && !repeated) {
                    currentOnClick()
                }
            }
        }
    }
}
