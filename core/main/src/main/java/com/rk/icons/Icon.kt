package com.rk.icons

import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

sealed class Icon {
    class DrawableRes(@androidx.annotation.DrawableRes val drawableRes: Int) : Icon()

    class VectorIcon(val vector: ImageVector) : Icon()

    class SvgIcon(val file: File) : Icon()
}
