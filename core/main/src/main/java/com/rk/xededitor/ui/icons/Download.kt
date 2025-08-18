package com.rk.xededitor.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val XedIcons.Download: ImageVector
    get() {
        if (_Download != null) return _Download!!

        _Download = ImageVector.Builder(
            name = "Download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(480f, 640f)
                lineTo(280f, 440f)
                lineToRelative(56f, -58f)
                lineToRelative(104f, 104f)
                verticalLineToRelative(-326f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(326f)
                lineToRelative(104f, -104f)
                lineToRelative(56f, 58f)
                close()
                moveTo(240f, 800f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(160f, 720f)
                verticalLineToRelative(-120f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(120f)
                horizontalLineToRelative(480f)
                verticalLineToRelative(-120f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(120f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(720f, 800f)
                close()
            }
        }.build()

        return _Download!!
    }

private var _Download: ImageVector? = null

