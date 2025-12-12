package com.rk.icons

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Icon {
    class DrawableIcon(val drawable: Drawable) : Icon()

    class DrawableRes(@androidx.annotation.DrawableRes val drawableRes: Int) : Icon()

    class VectorIcon(val vector: ImageVector) : Icon()
}
