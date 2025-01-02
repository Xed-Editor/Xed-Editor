package com.rk.xededitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rk.xededitor.R

/*
 * Tipography definition for theming
 */
val OutfitFontFamily = FontFamily(Font(R.font.outfit_regular, FontWeight.Normal, FontStyle.Normal))

/*
 * Overrides the default typo
 */
val Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
    )
