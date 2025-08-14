package com.rk.xededitor.ui.screens.settings

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.category.PreferenceCategory
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures

@Composable
fun SettingsScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.settings), backArrowVisible = true) {
        Categories(navController)
    }
}

@Composable
private fun Categories(navController: NavController) {
    PreferenceCategory(
        label = stringResource(id = strings.app),
        description = stringResource(id = strings.app_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
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

    /*
    PreferenceTemplate(modifier = Modifier
        .padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.large)
        .clickable { navController.navigate(SettingsRoutes.Misc.route)  }
        .background(Color.Transparent),
        verticalPadding = 14.dp,
        title = {
            Text(stringResource(id = strings.misc))
        },
        description = {
            Text(stringResource(id = strings.misc_desc))
        },
        startWidget = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    ) */

    PreferenceCategory(
        label = stringResource(id = strings.feature_toggles),
        description = stringResource(id = strings.feature_toggles_desc),
        iconResource = drawables.settings,
        onNavigate = { navController.navigate(SettingsRoutes.FeatureToggles.route) },
    )

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
            label = "Debug Options",
            description = "Debugging options for ${strings.app_name.getString()}",
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

