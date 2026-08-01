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
import com.rk.common.PackageType
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayout
import com.rk.extension.Extension
import com.rk.extension.InstallState
import com.rk.extension.extensionManager
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.Package
import com.rk.extension.model.UpdatablePackage
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.ThemeManager
import com.rk.theme.Typography
import com.rk.utils.formatFileSize
import com.rk.utils.formatNumberCompact
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PackageDetail(pkg: Package?, navController: NavController) {
    val scope = rememberCoroutineScope()
    val dialogManager = remember { ExtensionDialogManager() }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showSourceCodeSheet by remember { mutableStateOf(false) }

    val notFoundRes =
        when (pkg?.type) {
            PackageType.THEME -> strings.theme_not_found
            PackageType.ICON_PACK -> strings.icon_pack_not_found
            else -> strings.ext_not_found
        }

    RefreshablePreferenceLayout(
        label = pkg?.name ?: stringResource(notFoundRes),
        backArrowVisible = true,
        isExpandedScreen = true,
        actions = {
            pkg?.repository?.let {
                IconButton(onClick = { showSourceCodeSheet = true }) {
                    Icon(painter = painterResource(drawables.xml), contentDescription = null)
                }
            }

            if (pkg?.hasSettings == true) {
                IconButton(
                    enabled = extensionManager.isInstalled(pkg.id),
                    onClick = { navController.navigate("${SettingsRoutes.ExtensionSettings.route}/${pkg.id}") },
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

        if (pkg == null) {
            Text(stringResource(notFoundRes), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            val installState = rememberPackageInstallState(pkg)

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutSection(
                    pkg = pkg,
                    refreshKey = refreshKey,
                    installState = installState,
                    updateInstallState = {
                        if (pkg.type == PackageType.EXTENSION && extensionManager.getExtension(pkg.id) == null) {
                            navController.popBackStack()
                        } else if (pkg.type == PackageType.THEME && ThemeManager.getTheme(pkg.id) == null) {
                            navController.popBackStack()
                        } else if (
                            pkg.type == PackageType.ICON_PACK && App.iconPackManager.getIconPackPackage(pkg.id) == null
                        ) {
                            navController.popBackStack()
                        }
                    },
                    scope = scope,
                    dialogManager = dialogManager,
                )
            }
            TabSection(pkg, scope, refreshKey, onLoaded = { isRefreshing = false })

            if (showSourceCodeSheet) {
                SourceCodeSheet(pkg) { showSourceCodeSheet = false }
            }
        }
    }
}

@Composable
private fun AboutSection(
    pkg: Package,
    refreshKey: Int,
    installState: InstallState,
    updateInstallState: (InstallState) -> Unit,
    scope: CoroutineScope,
    dialogManager: ExtensionDialogManager,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val placeholder =
            when (pkg.type) {
                PackageType.THEME -> drawables.palette
                PackageType.ICON_PACK -> drawables.widgets
                else -> drawables.extension
            }

        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(pkg.iconUrl)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
            contentDescription = null,
        )

        Column {
            Text(
                text = pkg.name,
                style = Typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier =
                        Modifier.clickable(
                            enabled = pkg.author.github != null,
                            onClick = {
                                val githubProfileUrl = pkg.author.github?.let { "https://github.com/$it" }
                                githubProfileUrl?.let {
                                    val intent = Intent(Intent.ACTION_VIEW, it.toUri())
                                    context.startActivity(intent)
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtensionAuthorIcon(
                        pkg.author,
                        Modifier.size(24.dp).padding(end = 4.dp),
                    )
                    Text(
                        text = "${pkg.author}",
                        style = Typography.labelLarge,
                        color =
                            if (pkg.author.github != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = " • v${pkg.version}",
                    style = Typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val newVersion = (pkg as? UpdatablePackage)?.newVersion

                newVersion?.let {
                    Text(
                        text = " → v$it",
                        style = Typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (pkg.type == PackageType.EXTENSION) {
                    var isCrashed by remember {
                        mutableStateOf(extensionManager.isExtensionCrashed(pkg as Extension))
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
                                            extensionManager.setExtensionCrashed(pkg as Extension, false)
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
    }

    LaunchedEffect(refreshKey) {
        if (pkg.type == PackageType.EXTENSION) {
            extensionManager.invalidateSize(pkg as Extension)
        }
    }

    val size =
        remember(pkg.size) {
            pkg.size?.let { formatFileSize(it) } ?: "---"
        }

    val rating = pkg.rating?.toString() ?: "---"
    val showStar = pkg.rating != null

    val downloadCount =
        remember(pkg.downloads) {
            pkg.downloads?.let { formatNumberCompact(it) } ?: "---"
        }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PackageStats(Modifier.weight(1f), stringResource(strings.downloads).uppercase(), downloadCount)
        PackageStats(
            Modifier.weight(1f),
            stringResource(strings.rating).uppercase(),
            rating,
            if (showStar) Icons.Default.Star else null,
        )
        PackageStats(Modifier.weight(1f), stringResource(strings.size).uppercase(), size)
    }

    val xedVersionCode = App.versionCode
    val minAppVersion = pkg.minAppVersion
    val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion

    val currentArchitecture = Build.SUPPORTED_ABIS.firstOrNull()
    val supportedArchitecture =
        currentArchitecture == null || pkg.supportedArchitectures?.contains(currentArchitecture) ?: true

    ExtensionActionButtons(
        outdatedWarning = outdatedClient || !supportedArchitecture,
        installState = installState,
        scope = scope,
        progress = StoreManager.downloadProgress[pkg.id] ?: 0f,
        onInstallClick = {
            runPackageInstallAction(pkg, updateInstallState, context, activity, dialogManager)
        },
        onUninstallClick = {
            runPackageUninstallAction(pkg, updateInstallState, scope, activity)
        },
        onUpdateClick = {
            runPackageUpdateAction(pkg, updateInstallState, context, activity, dialogManager)
        },
        showRecommendedButton = pkg.type == PackageType.EXTENSION && getRecommendations(pkg as Extension).isNotEmpty(),
        onRecommendedClick = {
            if (pkg is Extension) {
                dialogManager.showRecommendations(pkg, getRecommendations(pkg))
            }
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
fun PackageStats(modifier: Modifier = Modifier, title: String, value: String, trailingVector: ImageVector? = null) {
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

enum class PackageRoutes(val icon: Icon, val label: String, val route: String) {
    OVERVIEW(Icon.ResourceIcon(drawables.file), strings.overview.getString(), "overview"),
    REVIEWS(Icon.ResourceIcon(drawables.comment), strings.reviews.getString(), "reviews"),
    CHANGELOG(Icon.ResourceIcon(drawables.update), strings.changelog.getString(), "changelog"),
}

@Composable
private fun TabSection(pkg: Package, scope: CoroutineScope, refreshKey: Int, onLoaded: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0) { PackageRoutes.entries.size }

    PrimaryScrollableTabRow(edgePadding = 16.dp, selectedTabIndex = pagerState.currentPage) {
        PackageRoutes.entries.forEachIndexed { index, destination ->
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
            when (PackageRoutes.entries[page]) {
                PackageRoutes.OVERVIEW -> MarkdownViewer(pkg.readmeUrl, refreshKey, onLoaded)
                PackageRoutes.REVIEWS -> ReviewsPage(pkg, refreshKey, onLoaded)
                PackageRoutes.CHANGELOG -> MarkdownViewer(pkg.changelogUrl, refreshKey, onLoaded)
            }
        }
    }
}
