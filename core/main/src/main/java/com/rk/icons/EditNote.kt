package com.rk.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val XedIcons.Edit_note: ImageVector
    get() {
        if (_Edit_note != null) return _Edit_note!!

        _Edit_note =
            ImageVector.Builder(
                    name = "Edit_note",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(fill = SolidColor(Color(0xFF000000))) {
                        moveTo(160f, 560f)
                        verticalLineToRelative(-80f)
                        horizontalLineToRelative(280f)
                        verticalLineToRelative(80f)
                        close()
                        moveToRelative(0f, -160f)
                        verticalLineToRelative(-80f)
                        horizontalLineToRelative(440f)
                        verticalLineToRelative(80f)
                        close()
                        moveToRelative(0f, -160f)
                        verticalLineToRelative(-80f)
                        horizontalLineToRelative(440f)
                        verticalLineToRelative(80f)
                        close()
                        moveToRelative(360f, 560f)
                        verticalLineToRelative(-123f)
                        lineToRelative(221f, -220f)
                        quadToRelative(9f, -9f, 20f, -13f)
                        reflectiveQuadToRelative(22f, -4f)
                        quadToRelative(12f, 0f, 23f, 4.5f)
                        reflectiveQuadToRelative(20f, 13.5f)
                        lineToRelative(37f, 37f)
                        quadToRelative(8f, 9f, 12.5f, 20f)
                        reflectiveQuadToRelative(4.5f, 22f)
                        reflectiveQuadToRelative(-4f, 22.5f)
                        reflectiveQuadToRelative(-13f, 20.5f)
                        lineTo(643f, 800f)
                        close()
                        moveToRelative(300f, -263f)
                        lineToRelative(-37f, -37f)
                        close()
                        moveTo(580f, 740f)
                        horizontalLineToRelative(38f)
                        lineToRelative(121f, -122f)
                        lineToRelative(-18f, -19f)
                        lineToRelative(-19f, -18f)
                        lineToRelative(-122f, 121f)
                        close()
                        moveToRelative(141f, -141f)
                        lineToRelative(-19f, -18f)
                        lineToRelative(37f, 37f)
                        close()
                    }
                }
                .build()

        return _Edit_note!!
    }

private var _Edit_note: ImageVector? = null
