package com.rk.ai.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SparklesTiny: ImageVector
    get() {
        if (_SparklesTiny != null) return _SparklesTiny!!

        _SparklesTiny = ImageVector.Builder(
            name = "sparkles",
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
                moveTo(16f, 18f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                moveToRelative(0f, -12f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                moveToRelative(-7f, 12f)
                arcToRelative(6f, 6f, 0f, false, true, 6f, -6f)
                arcToRelative(6f, 6f, 0f, false, true, -6f, -6f)
                arcToRelative(6f, 6f, 0f, false, true, -6f, 6f)
                arcToRelative(6f, 6f, 0f, false, true, 6f, 6f)
            }
        }.build()

        return _SparklesTiny!!
    }

private var _SparklesTiny: ImageVector? = null