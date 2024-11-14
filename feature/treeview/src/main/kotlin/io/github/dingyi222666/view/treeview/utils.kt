package io.github.dingyi222666.view.treeview

import android.view.MotionEvent

internal fun generateTranslatedMotionEvent(origin: MotionEvent, dx: Float, dy: Float) =
    MotionEvent.obtain(
        origin.downTime - 1,
        origin.eventTime,
        origin.action,
        origin.pointerCount,
        Array(origin.pointerCount) { index ->
            MotionEvent.PointerProperties().also {
                origin.getPointerProperties(index, it)
            }
        },
        Array(origin.pointerCount) { index ->
            MotionEvent.PointerCoords().also {
                origin.getPointerCoords(index, it)
                it.x += dx
                it.y += dy
            }
        },
        origin.metaState,
        origin.buttonState,
        origin.xPrecision,
        origin.yPrecision,
        origin.deviceId,
        origin.edgeFlags,
        origin.source,
        origin.flags
    )