package com.rk.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DesignTokens {
    object Spacing {
        val none = 0.dp
        val xxsmall = 2.dp
        val xsmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val xlarge = 24.dp
        val xxlarge = 32.dp
        val xxxlarge = 48.dp
    }

    object TabSize {
        val compactHeight = 36.dp
        val regularHeight = 44.dp
        val minTabWidth = 80.dp
        val maxTabWidth = 200.dp
    }

    object TopBarSize {
        val compactHeight = 44.dp
        val regularHeight = 56.dp
    }

    object NavigationRailSize {
        val compactWidth = 52.dp
        val regularWidth = 72.dp
    }

    object CornerRadius {
        val none = RoundedCornerShape(0.dp)
        val xsmall = RoundedCornerShape(2.dp)
        val small = RoundedCornerShape(4.dp)
        val medium = RoundedCornerShape(8.dp)
        val large = RoundedCornerShape(12.dp)
        val xlarge = RoundedCornerShape(16.dp)
        val full = RoundedCornerShape(50)
    }

    object Elevation {
        val none = 0.dp
        val xsmall = 0.5.dp
        val small = 1.dp
        val medium = 2.dp
        val large = 4.dp
        val xlarge = 8.dp
    }

    object Divider {
        val thin = 0.5.dp
        val regular = 1.dp
    }

    object BottomSheet {
        val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        val shapeTablet = RoundedCornerShape(20.dp)
        val scrimColor = Color.Black.copy(alpha = 0.32f)
        val elevation = 12.dp
        val elevationTablet = 20.dp
        val contentPadding = large
    }
}

val XedShapes = Shapes(
    extraSmall = DesignTokens.CornerRadius.xsmall,
    small = DesignTokens.CornerRadius.small,
    medium = DesignTokens.CornerRadius.medium,
    large = DesignTokens.CornerRadius.large,
    extraLarge = DesignTokens.CornerRadius.xlarge,
)
