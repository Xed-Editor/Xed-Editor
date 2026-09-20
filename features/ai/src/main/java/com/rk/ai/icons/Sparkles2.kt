package com.rk.ai.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Sparkles2: ImageVector
    get() {
        if (_Sparkles2 != null) return _Sparkles2!!

        _Sparkles2 = ImageVector.Builder(
            name = "sparkles-2",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13f, 7f)
                arcToRelative(9.3f, 9.3f, 0f, false, false, 1.516f, -0.546f)
                curveToRelative(0.911f, -0.438f, 1.494f, -1.015f, 1.937f, -1.932f)
                curveToRelative(0.207f, -0.428f, 0.382f, -0.928f, 0.547f, -1.522f)
                curveToRelative(0.165f, 0.595f, 0.34f, 1.095f, 0.547f, 1.521f)
                curveToRelative(0.443f, 0.918f, 1.026f, 1.495f, 1.937f, 1.933f)
                curveToRelative(0.426f, 0.205f, 0.925f, 0.38f, 1.516f, 0.546f)
                arcToRelative(9.3f, 9.3f, 0f, false, false, -1.516f, 0.547f)
                curveToRelative(-0.911f, 0.438f, -1.494f, 1.015f, -1.937f, 1.932f)
                arcToRelative(9f, 9f, 0f, false, false, -0.547f, 1.521f)
                curveToRelative(-0.165f, -0.594f, -0.34f, -1.095f, -0.547f, -1.521f)
                curveToRelative(-0.443f, -0.918f, -1.026f, -1.494f, -1.937f, -1.932f)
                arcToRelative(9f, 9f, 0f, false, false, -1.516f, -0.547f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 14f)
                arcToRelative(21f, 21f, 0f, false, false, 1.652f, -0.532f)
                curveToRelative(2.542f, -0.953f, 3.853f, -2.238f, 4.816f, -4.806f)
                arcToRelative(20f, 20f, 0f, false, false, 0.532f, -1.662f)
                arcToRelative(20f, 20f, 0f, false, false, 0.532f, 1.662f)
                curveToRelative(0.963f, 2.567f, 2.275f, 3.853f, 4.816f, 4.806f)
                quadToRelative(0.75f, 0.28f, 1.652f, 0.532f)
                arcToRelative(21f, 21f, 0f, false, false, -1.652f, 0.532f)
                curveToRelative(-2.542f, 0.953f, -3.854f, 2.238f, -4.816f, 4.806f)
                arcToRelative(20f, 20f, 0f, false, false, -0.532f, 1.662f)
                arcToRelative(20f, 20f, 0f, false, false, -0.532f, -1.662f)
                curveToRelative(-0.963f, -2.568f, -2.275f, -3.853f, -4.816f, -4.806f)
                arcToRelative(21f, 21f, 0f, false, false, -1.652f, -0.532f)
            }
        }.build()

        return _Sparkles2!!
    }

private var _Sparkles2: ImageVector? = null