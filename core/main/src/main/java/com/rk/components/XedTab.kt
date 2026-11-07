package com.rk.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 [Tab] with adjusted content colors:
 * - Selected tab is tinted with primary color
 * - Other tabs have the normal surface color
 */
@Composable
fun XedTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color? = null,
    unselectedContentColor: Color? = null,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selectedContentColor = selectedContentColor ?: MaterialTheme.colorScheme.primary,
        unselectedContentColor = unselectedContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource,
        text = text,
        icon = icon,
    )
}

/**
 * Material Design 3 [LeadingIconTab] with adjusted content colors:
 * - Selected tab is tinted with primary color
 * - Other tabs have the normal surface color
 */
@Composable
fun LeadingIconXedTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    selectedContentColor: Color? = null,
    unselectedContentColor: Color? = null,
) {
    LeadingIconTab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selectedContentColor = selectedContentColor ?: MaterialTheme.colorScheme.primary,
        unselectedContentColor = unselectedContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource,
        text = text,
        icon = icon,
    )
}
