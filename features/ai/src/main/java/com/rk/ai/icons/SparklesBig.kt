package com.rk.ai.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SparklesBig: ImageVector
    get() {
        if (_SparklesBig != null) return _SparklesBig!!

        _SparklesBig = ImageVector.Builder(
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
                moveTo(11.017f, 2.814f)
                arcToRelative(1f, 1f, 0f, false, true, 1.966f, 0f)
                lineToRelative(1.051f, 5.558f)
                arcToRelative(2f, 2f, 0f, false, false, 1.594f, 1.594f)
                lineToRelative(5.558f, 1.051f)
                arcToRelative(1f, 1f, 0f, false, true, 0f, 1.966f)
                lineToRelative(-5.558f, 1.051f)
                arcToRelative(2f, 2f, 0f, false, false, -1.594f, 1.594f)
                lineToRelative(-1.051f, 5.558f)
                arcToRelative(1f, 1f, 0f, false, true, -1.966f, 0f)
                lineToRelative(-1.051f, -5.558f)
                arcToRelative(2f, 2f, 0f, false, false, -1.594f, -1.594f)
                lineToRelative(-5.558f, -1.051f)
                arcToRelative(1f, 1f, 0f, false, true, 0f, -1.966f)
                lineToRelative(5.558f, -1.051f)
                arcToRelative(2f, 2f, 0f, false, false, 1.594f, -1.594f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 2f)
                verticalLineToRelative(4f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 4f)
                horizontalLineToRelative(-4f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 20f)
                arcTo(2f, 2f, 0f, false, true, 4f, 22f)
                arcTo(2f, 2f, 0f, false, true, 2f, 20f)
                arcTo(2f, 2f, 0f, false, true, 6f, 20f)
                close()
            }
        }.build()

        return _SparklesBig!!
    }

private var _SparklesBig: ImageVector? = null