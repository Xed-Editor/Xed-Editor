package com.rk.xededitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.activities.settings.settingsNavController

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NextScreenCard(
    modifier: Modifier = Modifier,
    navController: NavController? = settingsNavController.get(),
    label: String,
    description: String? = null,
    route: SettingsRoutes,
    isEnabled: Boolean = true,
    icon:ImageVector? = null,
    @DrawableRes iconRes:Int? = null,
    startIconTint:Color = LocalContentColor.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    PreferenceTemplate(
        modifier = modifier.combinedClickable(
            enabled = isEnabled,
            indication = ripple(),
            interactionSource = interactionSource,
            onClick = {
                navController?.navigate(route.route)
            }
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(fontWeight = FontWeight.Bold, text = label) },
        description = { description?.let { Text(text = it) } },
        enabled = true,
        applyPaddings = false,
        endWidget = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = route.route)
        },
        startWidget = {
            if (icon != null) {
                Icon(modifier = Modifier.size(24.dp),
                    imageVector = icon,
                    tint = startIconTint,
                    contentDescription = label)
            }else if (iconRes != null){
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    )

}