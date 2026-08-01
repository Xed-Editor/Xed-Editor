package com.rk.settings.extension

import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayout
import com.rk.extension.Extension
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.manager.StoreManager
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.Typography
import com.rk.utils.formatFileSize
import com.rk.utils.formatNumberCompact
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ExtensionDetail(extension: Extension?, navController: NavController) {
    val scope = rememberCoroutineScope()
    val dialogManager = remember { ExtensionDialogManager() }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showSourceCodeSheet by remember { mutableStateOf(false) }

    RefreshablePreferenceLayout(
        label = extension?.name ?: stringResource(strings.ext_not_found),
        backArrowVisible = true,
        isExpandedScreen = true,
        actions = {
            IconButton(onClick = { showSourceCodeSheet = true }) {
                Icon(painter = painterResource(drawables.xml), contentDescription = null)
            }

            if (extension?.hasSettings == true) {
                IconButton(
                    enabled = extensionManager.isInstalled(extension.id),
                    onClick = { navController.navigate("${SettingsRoutes.ExtensionSettings.route}/${extension.id}") },
                ) {
                    Icon(
                        painter = painterResource(drawables.settings),
                        contentDescription = stringResource(strings.settings),
                    )
                }
            }
        },
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshKey++
        },
    ) {
        ExtensionDialogRenderer(dialogManager)

        if (extension == null) {
            Text(stringResource(strings.ext_not_found_desc), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            val installState = rememberInstallState(extension)

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutSection(
                    extension = extension,
                    refreshKey = refreshKey,
                    installState = installState,
                    updateInstallState = {
                        if (extensionManager.getExtension(extension.id) == null) {
                            navController.popBackStack()
                        }
                    },
                    scope = scope,
                    dialogManager = dialogManager,
                )
            }
            TabSection(extension, scope, refreshKey, onLoaded = { isRefreshing = false })

            if (showSourceCodeSheet) {
                SourceCodeSheet(extension) { showSourceCodeSheet = false }
            }
        }
    }
}

@Composable
private fun AboutSection(
    extension: Extension,
    refreshKey: Int,
    installState: InstallState,
    updateInstallState: (InstallState) -> Unit,
    scope: CoroutineScope,
    dialogManager: ExtensionDialogManager,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(extension.iconUrl)
                    .placeholder(drawables.extension)
                    .error(drawables.extension)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
            contentDescription = null,
        )

        Column {
            Text(
                text = extension.name,
                style = Typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier =
                        Modifier.clickable(
                            enabled = extension.author.github != null,
                            onClick = {
                                val githubProfileUrl = extension.author.github.let { "https://github.com/$it" }
                                val intent = Intent(Intent.ACTION_VIEW, githubProfileUrl.toUri())
                                context.startActivity(intent)
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtensionAuthorIcon(
                        extension.author,
                        Modifier.size(24.dp).padding(end = 4.dp),
                    )
                    Text(
                        text = "${extension.author}",
                        style = Typography.labelLarge,
                        color =
                            if (extension.author.github != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = " • v${extension.version}",
                    style = Typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val isUpdatable = extension is UpdatableExtension && extension.hasUpdate()
                if (isUpdatable) {
                    Text(
                        text = " → v${extension.newVersion}",
                        style = Typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                var isCrashed by remember {
                    mutableStateOf(extensionManager.isExtensionCrashed(extension))
                }
                if (isCrashed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = " • ",
                            style = Typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )

                        Text(
                            text = stringResource(strings.disabled_crashed),
                            style = Typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.clickable(
                                    onClick = {
                                        // TODO: Crash detail screen
                                        extensionManager.setExtensionCrashed(extension, false)
                                        toast("Re-enabled extension. Restart the app for changes to take effect.")
                                        isCrashed = false
                                    }
                                ),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(refreshKey) {
        extensionManager.invalidateSize(extension)
    }

    val size =
        remember(extension.size) {
            extension.size?.let { formatFileSize(it) } ?: "---"
        }

    val rating = extension.rating?.toString() ?: "---"
    val showStar = extension.rating != null

    val downloadCount =
        remember(extension.downloads) {
            extension.downloads?.let { formatNumberCompact(it) } ?: "---"
        }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExtensionStats(Modifier.weight(1f), stringResource(strings.downloads).uppercase(), downloadCount)
        ExtensionStats(
            Modifier.weight(1f),
            stringResource(strings.rating).uppercase(),
            rating,
            if (showStar) Icons.Default.Star else null,
        )
        ExtensionStats(Modifier.weight(1f), stringResource(strings.size).uppercase(), size)
    }

    val xedVersionCode = App.versionCode
    val minAppVersion = extension.minAppVersion
    val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion

    val currentArchitecture = Build.SUPPORTED_ABIS.firstOrNull()
    val supportedArchitecture =
        currentArchitecture == null || extension.supportedArchitectures?.contains(currentArchitecture) ?: true

    val recommendations = getRecommendations(extension)

    ExtensionActionButtons(
        outdatedWarning = outdatedClient || !supportedArchitecture,
        installState = installState,
        scope = scope,
        progress = StoreManager.downloadProgress[extension.id] ?: 0f,
        onInstallClick = {
            val action = {
                val missing = getMissingDependencies(extension)
                if (missing.isNotEmpty()) {
                    dialogManager.showDependencies(extension, missing) {
                        runExtensionInstallAction(extension, updateInstallState, context, activity)
                    }
                } else {
                    runExtensionInstallAction(extension, updateInstallState, context, activity)
                }
            }

            if (Settings.warn_extensions) {
                dialogManager.showWarning(action)
            } else {
                action()
            }
        },
        onUninstallClick = { runExtensionUninstallAction(extension, updateInstallState, scope, activity) },
        onUpdateClick = {
            if (extension !is UpdatableExtension) return@ExtensionActionButtons

            val missing = getMissingDependencies(extension)
            if (missing.isNotEmpty()) {
                dialogManager.showDependencies(extension, missing) {
                    runExtensionUpdateAction(extension, updateInstallState, context, activity)
                }
            } else {
                runExtensionUpdateAction(extension, updateInstallState, context, activity)
            }
        },
        showRecommendedButton = recommendations.isNotEmpty(),
        onRecommendedClick = {
            dialogManager.showRecommendations(extension, recommendations)
        },
    )

    if (outdatedClient || !supportedArchitecture) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = stringResource(strings.warning),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(if (outdatedClient) strings.outdated_client else strings.unsupported_architecture),
                style = MaterialTheme.typography.labelMedium,
            )
        }
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
    OVERVIEW(Icon.ResourceIcon(drawables.file), strings.overview.getString(), "overview"),
    REVIEWS(Icon.ResourceIcon(drawables.comment), strings.reviews.getString(), "reviews"),
    CHANGELOG(Icon.ResourceIcon(drawables.update), strings.changelog.getString(), "changelog"),
}

@Composable
private fun TabSection(extension: Extension, scope: CoroutineScope, refreshKey: Int, onLoaded: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0) { ExtensionRoutes.entries.size }

    PrimaryScrollableTabRow(edgePadding = 16.dp, selectedTabIndex = pagerState.currentPage) {
        ExtensionRoutes.entries.forEachIndexed { index, destination ->
            LeadingIconTab(
                icon = { XedIcon(destination.icon) },
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(text = destination.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        verticalAlignment = Alignment.Top,
        pageSpacing = 16.dp,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when (ExtensionRoutes.entries[page]) {
                ExtensionRoutes.OVERVIEW -> MarkdownViewer(extension.readmeUrl, refreshKey, onLoaded)
                ExtensionRoutes.REVIEWS -> ReviewsPage(extension, refreshKey, onLoaded)
                ExtensionRoutes.CHANGELOG -> MarkdownViewer(extension.changelogUrl, refreshKey, onLoaded)
            }
        }
    }
}
