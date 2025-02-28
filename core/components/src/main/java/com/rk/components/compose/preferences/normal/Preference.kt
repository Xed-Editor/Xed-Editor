package com.rk.components.compose.preferences.normal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.components.compose.preferences.base.PreferenceTemplate

/*
 * A Normal preference used in settings
 * @author Aquiles Trindade (trindadedev).
 */

@Composable
fun Preference(
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    text: @Composable () -> Unit,
    secondaryText: @Composable (() -> Unit) = {},
    trailing: @Composable (() -> Unit) = {},
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    PreferenceTemplate(
        modifier =
            modifier.clickable(
                indication = ripple(),
                interactionSource = interactionSource,
                onClick = { onClick?.invoke() },
            ),
        contentModifier = Modifier.fillMaxHeight().padding(vertical = 16.dp).padding(start = 16.dp),
        title = {
            ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp)) { text() }
        },
        description = {
            ProvideTextStyle(
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            ) {
                secondaryText()
            }
        },
        endWidget = {
            if (icon != null) {
                Box(modifier = Modifier.padding(8.dp)) { icon() }
            }
            trailing()
        },
        enabled = true,
        applyPaddings = false,
    )
}
