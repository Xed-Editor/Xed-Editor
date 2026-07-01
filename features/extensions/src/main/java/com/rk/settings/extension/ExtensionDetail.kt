package com.rk.settings.extension

import android.content.Intent
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.rk.extension.manager.ExtensionRegistry
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.Typography
import com.rk.utils.formatFileSize
import com.rk.utils.formatNumberCompact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ExtensionDetail(extension: Extension?, navController: NavController) {
    val scope = rememberCoroutineScope()

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
                    onClick = { navController.navigate("${SettingsRoutes.ExtensionSettings.route}/{extensionId}") },
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
        if (extension == null) {
            Text(stringResource(strings.ext_not_found_desc), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            var localInstallState by remember {
                mutableStateOf(
                    if (extensionManager.isInstalled(extension.id)) {
                        if (extension is UpdatableExtension && extension.hasUpdate()) {
                            InstallState.Updatable
                        } else {
                            InstallState.Installed
                        }
                    } else {
                        InstallState.Idle
                    }
                )
            }

            val installState =
                remember(extension, localInstallState, ExtensionRegistry.activeInstalls[extension.id]) {
                    val active = ExtensionRegistry.activeInstalls[extension.id]
                    if (active != null) {
                        active
                    } else {
                        localInstallState
                    }
                }

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutSection(
                    extension = extension,
                    refreshKey = refreshKey,
                    installState = installState,
                    updateInstallState = {
                        localInstallState = it
                        if (it == InstallState.Idle && extensionManager.storeExtension[extension.id] == null) {
                            navController.popBackStack()
                        }
                    },
                    scope = scope,
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
                    ExtensionAuthorIcon(extension.author, Modifier.size(24.dp).padding(end = 4.dp))
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
            }
        }
    }

    var size by remember { mutableStateOf("---") }
    var rating by remember { mutableStateOf("---") }
    var downloadCount by remember { mutableStateOf("---") }
    var showStar by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        val stats = extension.getStats()
        stats.size?.let { size = formatFileSize(it) }
        stats.rating?.let {
            rating = it.toString()
            showStar = true
        }
        stats.downloadCount?.let { downloadCount = formatNumberCompact(it) }
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

    val minAppVersion = extension.minAppVersion
    val maxAppVersion = extension.maxAppVersion

    val xedVersionCode = App.versionCode
    val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion
    val outdatedExtension = maxAppVersion != null && xedVersionCode > maxAppVersion

    val progress = ExtensionRegistry.downloadProgress[extension.id]
    if (progress != null) {
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    ExtensionActionButtons(
        outdatedWarning = outdatedClient || outdatedExtension,
        modifier = Modifier.fillMaxWidth(),
        installState = installState,
        scope = scope,
        progress = progress,
        onInstallClick = {
            checkExtensionWarningAndRun(activity) {
                runExtensionInstallAction(extension, updateInstallState, context, activity)
            }
        },
        onUninstallClick = { runExtensionUninstallAction(extension, updateInstallState, scope, activity) },
        onUpdateClick = {
            if (extension !is UpdatableExtension) return@ExtensionActionButtons
            runExtensionUpdateAction(extension, updateInstallState, context, activity)
        },
    )

    if (outdatedClient || outdatedExtension) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = stringResource(strings.warning),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(if (outdatedClient) strings.outdated_client else strings.outdated_extension),
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
