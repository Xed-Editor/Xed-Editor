package com.rk.icons

import androidx.compose.ui.graphics.vector.ImageVector

sealed class Icon {
    class DrawableRes(@androidx.annotation.DrawableRes val drawableRes: Int) : Icon()

    class VectorIcon(val vector: ImageVector) : Icon()
}
