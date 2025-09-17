package com.rk.xededitor.ui.screens.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.category.PreferenceCategory
import com.rk.extension.Hooks
import com.rk.libcommons.openUrl
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.NextScreenCard
import com.rk.xededitor.ui.icons.Menu_book
import com.rk.xededitor.ui.icons.XedIcons
import com.rk.xededitor.ui.screens.settings.app.InbuiltFeatures

@Composable
fun SettingsScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.settings), backArrowVisible = true) {
        Categories(navController)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Categories(navController: NavController) {
    val activity = LocalActivity.current


    PreferenceTemplate(modifier = Modifier
        .padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.large)
        .clickable { navController.navigate(SettingsRoutes.Support.route)  }
        .background(Color.Transparent),
        verticalPadding = 14.dp,
        title = {
            Text("Support")
        },
        description = {
            Text(stringResource(id = strings.sponsor_desc))
        },
        startWidget = {
            HeartbeatIcon()
        }
    )


    PreferenceCategory(
        label = stringResource(id = strings.app),
        description = stringResource(id = strings.app_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(strings.themes),
        description = stringResource(strings.theme_settings),
        iconResource = drawables.palette,
        onNavigate = { navController.navigate(SettingsRoutes.Themes.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.editor),
        description = stringResource(id = strings.editor_desc),
        iconResource = drawables.edit,
        onNavigate = { navController.navigate(SettingsRoutes.EditorSettings.route) },
    )

    if (InbuiltFeatures.terminal.state.value) {
        PreferenceCategory(
            label = stringResource(id = strings.terminal),
            description = stringResource(id = strings.terminal_desc),
            iconResource = drawables.terminal,
            onNavigate = { navController.navigate(SettingsRoutes.TerminalSettings.route) },
        )

        PreferenceCategory(
            label = stringResource(id = strings.runners),
            description = stringResource(id = strings.runners_desc),
            iconResource = drawables.run,
            onNavigate = { navController.navigate(SettingsRoutes.Runners.route) },
        )
    }

    if (InbuiltFeatures.extensions.state.value) {
        PreferenceCategory(
            label = stringResource(strings.ext),
            description = stringResource(strings.ext_desc),
            iconResource = drawables.extension,
            onNavigate = { navController.navigate(SettingsRoutes.Extensions.route) },
        )
    }


    if (App.isFDroid && InbuiltFeatures.developerOptions.state.value) {
        PreferenceCategory(
            label = stringResource(strings.debug_options),
            description = strings.debug_options_desc.getFilledString(mapOf("app_name" to strings.app_name.getString())),
            iconResource = drawables.build,
            onNavigate = { navController.navigate(SettingsRoutes.DeveloperOptions.route) },
        )
    }


    PreferenceTemplate(modifier = Modifier
        .padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.large)
        .clickable { navController.navigate(SettingsRoutes.About.route) }
        .background(Color.Transparent),
        verticalPadding = 14.dp,
        title = {
            Text(stringResource(id = strings.about))
        },
        description = {
            Text(stringResource(id = strings.about_desc))
        },
        startWidget = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    )


    Hooks.Settings.screens.values.forEach{ entry ->
        PreferenceTemplate(modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { navController.navigate(entry.route) }
            .background(Color.Transparent),
            verticalPadding = 14.dp,
            title = {
                Text(entry.label)
            },
            description = {
                Text(entry.description)
            },
            startWidget = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    entry.icon()
                }
            }
        )
    }


    PreferenceTemplate(
        modifier = Modifier.combinedClickable(
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = {
                activity?.openUrl("https://xed-editor.github.io/Xed-Docs/")
            }
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(fontWeight = FontWeight.Bold, text = stringResource(strings.docs)) },
        description = { Text(stringResource(strings.docs_desc)) },
        enabled = true,
        applyPaddings = false,
        endWidget = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null)
        },
        startWidget = {
            Icon(modifier = Modifier.size(24.dp),
                imageVector = XedIcons.Menu_book,
                tint = LocalContentColor.current,
                contentDescription = null)
        }
    )
}


@Composable
fun HeartbeatIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")

    val scale = infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .scale(scale.value)
    ) {
        Icon(
            imageVector = if (Settings.donated){Icons.Filled.Favorite}else{Icons.Outlined.FavoriteBorder},
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

