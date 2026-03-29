package com.rk.settings.extension

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App.Companion.extensionManager
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.extension.Extension
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.Typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ExtensionDetail(extension: Extension?) {
    val scope = rememberCoroutineScope()

    PreferenceLayout(
        label = extension?.name ?: stringResource(strings.ext_not_found),
        backArrowVisible = true,
        isExpandedScreen = true,
    ) {
        if (extension == null) {
            Text(stringResource(strings.ext_not_found_desc), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            var installState by remember {
                mutableStateOf(
                    if (extensionManager.isInstalled(extension.id)) {
                        InstallState.Installed
                    } else {
                        InstallState.Idle
                    }
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                AboutSection(extension, installState, { installState = it }, scope)
            }
            TabSection(scope)
        }
    }
}

@Composable
private fun AboutSection(
    extension: Extension,
    installState: InstallState,
    updateInstallState: (InstallState) -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity

    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data("https://github.com/KonerDev.png")
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).padding(end = 16.dp),
            contentDescription = null,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = extension.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            val license = extension.license.let { if (it.isBlank()) "" else " • $it" }
            Text(
                text = "by ${extension.authors.joinToString()} • v${extension.version}" + license,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ExtensionActionButton(
            extension = extension,
            installState = installState,
            scope = scope,
            onInstallClick = { runExtensionInstallAction(extension, updateInstallState, scope, context, activity) },
            onUninstallClick = { runExtensionUninstallAction(extension, updateInstallState, activity) },
        )
    }

    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExtensionStats(Modifier.weight(1f), stringResource(strings.downloads).uppercase(), "1.2M")
        ExtensionStats(Modifier.weight(1f), stringResource(strings.rating).uppercase(), "4.8", Icons.Default.Star)
        ExtensionStats(Modifier.weight(1f), stringResource(strings.size).uppercase(), "3.71KB")
    }
}

@Composable
fun ExtensionStats(modifier: Modifier = Modifier, title: String, value: String, trailingVector: ImageVector? = null) {
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = Typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                trailingVector?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

enum class ExtensionRoutes(val icon: Icon, val label: String, val route: String) {
    OVERVIEW(Icon.DrawableRes(drawables.file), strings.overview.getString(), "overview"),
    CONTRIBUTORS(Icon.DrawableRes(drawables.contributors), strings.contributors.getString(), "contributors"),
    CHANGELOG(Icon.DrawableRes(drawables.update), strings.changelog.getString(), "changelog"),
}

@Composable
private fun TabSection(scope: CoroutineScope) {
    val pagerState = rememberPagerState(initialPage = 0) { ExtensionRoutes.entries.size }

    PrimaryScrollableTabRow(edgePadding = 0.dp, selectedTabIndex = pagerState.currentPage) {
        ExtensionRoutes.entries.forEachIndexed { index, destination ->
            LeadingIconTab(
                icon = { XedIcon(destination.icon) },
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(text = destination.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = ExtensionRoutes.entries.size,
        pageSpacing = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        Column(modifier = Modifier.fillMaxSize()) {
            when (ExtensionRoutes.entries[page]) {
                else -> {
                    Text(ExtensionRoutes.entries[page].label)
                }
            }
        }
    }
}
