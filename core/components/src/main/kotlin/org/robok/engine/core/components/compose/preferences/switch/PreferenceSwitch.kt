package org.robok.engine.core.components.compose.preferences.switch

/*
 *  This file is part of Robok © 2024.
 *
 *  Robok is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Robok is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with Robok.  If not, see <https://www.gnu.org/licenses/>.
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate

/**
 * A Preference that provides a two-state toggleable option.
 *
 * @author Aquiles Trindade (trindadedev).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreferenceSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,

) {
    val interactionSource = remember { MutableInteractionSource() }

    PreferenceTemplate(
        modifier =
            modifier.combinedClickable(
                enabled = enabled,
                indication = ripple(),
                onLongClick = {
                    if (onLongClick != null) {
                        onLongClick()
                    }
                },
                interactionSource = interactionSource,
                onClick = {
                    if (onClick != null) {
                        onClick()
                    } else {
                        onCheckedChange(!checked)
                    }
                }
            ),
        contentModifier = Modifier.fillMaxHeight().padding(vertical = 16.dp).padding(start = 16.dp),
        title = { Text(fontWeight = FontWeight.Bold, text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            if (onClick != null) {
                Spacer(
                    modifier =
                        Modifier.height(32.dp)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Switch(
                modifier = Modifier.padding(all = 16.dp).height(24.dp),
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                interactionSource = interactionSource,
                colors =
                    SwitchDefaults.colors()
                        .copy(
                            uncheckedThumbColor = MaterialTheme.colorScheme.background,
                            uncheckedTrackColor =
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
            )
        },
        enabled = enabled,
        applyPaddings = false,
    )
}
