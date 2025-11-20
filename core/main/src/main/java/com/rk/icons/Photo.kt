package com.rk.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val XedIcons.Photo: ImageVector
    get() {
        if (_Photo != null) return _Photo!!

        _Photo =
            ImageVector.Builder(
                    name = "Photo",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(fill = SolidColor(Color(0xFF000000))) {
                        moveTo(200f, 840f)
                        quadToRelative(-33f, 0f, -56.5f, -23.5f)
                        reflectiveQuadTo(120f, 760f)
                        verticalLineToRelative(-560f)
                        quadToRelative(0f, -33f, 23.5f, -56.5f)
                        reflectiveQuadTo(200f, 120f)
                        horizontalLineToRelative(560f)
                        quadToRelative(33f, 0f, 56.5f, 23.5f)
                        reflectiveQuadTo(840f, 200f)
                        verticalLineToRelative(560f)
                        quadToRelative(0f, 33f, -23.5f, 56.5f)
                        reflectiveQuadTo(760f, 840f)
                        close()
                        moveToRelative(0f, -80f)
                        horizontalLineToRelative(560f)
                        verticalLineToRelative(-560f)
                        horizontalLineTo(200f)
                        close()
                        moveToRelative(40f, -80f)
                        horizontalLineToRelative(480f)
                        lineTo(570f, 480f)
                        lineTo(450f, 640f)
                        lineToRelative(-90f, -120f)
                        close()
                        moveToRelative(-40f, 80f)
                        verticalLineToRelative(-560f)
                        close()
                    }
                }
                .build()

        return _Photo!!
    }

private var _Photo: ImageVector? = null
