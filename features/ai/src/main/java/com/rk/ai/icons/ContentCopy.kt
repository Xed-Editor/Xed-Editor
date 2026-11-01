package com.rk.ai.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Material "content_copy" glyph; material-icons-core does not ship a copy icon. */
val ContentCopy: ImageVector
    get() {
        if (_ContentCopy != null) return _ContentCopy!!

        _ContentCopy =
            ImageVector.Builder(
                    name = "content_copy",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(16f, 1f)
                        horizontalLineTo(4f)
                        curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
                        verticalLineToRelative(14f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(12f)
                        verticalLineTo(1f)
                        close()
                    }
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(19f, 5f)
                        horizontalLineTo(8f)
                        curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
                        verticalLineToRelative(14f)
                        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                        horizontalLineToRelative(11f)
                        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                        verticalLineTo(7f)
                        curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                        close()
                        // Hole (wound the other way so the non-zero fill punches it out).
                        moveTo(19f, 21f)
                        horizontalLineTo(8f)
                        verticalLineTo(7f)
                        horizontalLineToRelative(11f)
                        verticalLineTo(21f)
                        close()
                    }
                }
                .build()

        return _ContentCopy!!
    }

private var _ContentCopy: ImageVector? = null
