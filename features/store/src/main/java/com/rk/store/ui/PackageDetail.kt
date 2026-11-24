package com.rk.store.ui

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.rk.common.PackageType
import com.rk.components.LeadingIconXedTab
import com.rk.components.XedCard
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayout
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.packages.InstallState
import com.rk.packages.model.Package
import com.rk.packages.model.UpdatablePackage
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.store.StoreRegistry
import com.rk.store.actions.rememberDownloadProgress
import com.rk.store.actions.rememberPackageActionContext
import com.rk.store.actions.rememberPackageInstallState
import com.rk.store.actions.runPackageInstallAction
import com.rk.store.actions.runPackageUninstallAction
import com.rk.store.actions.runPackageUpdateAction
import com.rk.theme.Typography
import com.rk.utils.formatFileSize
import com.rk.utils.formatNumberCompact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Detail screen shared by every package type.
 *
 * Type specific UI (extension crash state, recommendations, package settings, dialogs) is contributed by the
 * [com.rk.store.StoreSection] of the package's type.
 */
@Composable
fun PackageDetail(pkg: Package?, navController: NavController) {
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showSourceCodeSheet by remember { mutableStateOf(false) }

    val notFoundRes =
        when (pkg?.type) {
            PackageType.THEME -> strings.theme_not_found
            PackageType.ICON_PACK -> strings.icon_pack_not_found
            else -> strings.ext_not_found
        }

    val notFoundDescRes =
        when (pkg?.type) {
            PackageType.THEME -> strings.theme_not_found_desc
            PackageType.ICON_PACK -> strings.icon_pack_not_found_desc
            else -> strings.ext_not_found_desc
        }

    val section = pkg?.let { StoreRegistry.sectionFor(it.type) }

    Box {
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

                if (pkg != null && section != null) {
                    section.DetailActions(pkg, navController, this)
                }
            },
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                refreshKey++
            },
        ) {
            if (pkg == null || section == null) {
                Text(stringResource(notFoundDescRes), modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                val installState = rememberPackageInstallState(pkg)

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutSection(
                        pkg = pkg,
                        sectionType = pkg.type,
                        refreshKey = refreshKey,
                        installState = installState,
                        onPackageRemoved = {
                            if (section.getPackage(pkg.id) == null) {
                                navController.popBackStack()
                            }
                        },
                        scope = scope,
                    )
                }
                TabSection(pkg, scope, refreshKey, onLoaded = { isRefreshing = false })

                if (showSourceCodeSheet) {
                    SourceCodeSheet(pkg) { showSourceCodeSheet = false }
                }
            }
        }

        // Dialogs owned by the package's section (e.g. the extension security scan report).
        section?.Overlays()
    }
}

@Composable
private fun AboutSection(
    pkg: Package,
    sectionType: PackageType,
    refreshKey: Int,
    installState: InstallState,
    onPackageRemoved: () -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val action = rememberPackageActionContext(context, activity, scope, updateInstallState = { onPackageRemoved() })
    val section = StoreRegistry.sectionFor(sectionType)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PackageIcon(iconUrl = pkg.iconUrl, type = pkg.type, size = 70.dp)

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
                    PackageAuthorIcon(pkg.author, Modifier.size(24.dp).padding(end = 4.dp))
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

                val isUpdatable = pkg is UpdatablePackage && pkg.hasUpdate()
                if (isUpdatable) {
                    Text(
                        text = " → v${pkg.newVersion}",
                        style = Typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                section?.StatusBadge(pkg)
            }
        }
    }

    LaunchedEffect(refreshKey) { section?.invalidateSize(pkg) }

    val size = remember(pkg.size) { pkg.size?.let { formatFileSize(it) } ?: "---" }

    val rating = pkg.rating?.toString() ?: "---"
    val showStar = pkg.rating != null

    val downloadCount = remember(pkg.downloads) { pkg.downloads?.let { formatNumberCompact(it) } ?: "---" }

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

    PackageActionButtons(
        outdatedWarning = pkg.isOutdatedClient() || !pkg.isSupportedArchitecture(),
        installState = installState,
        scope = scope,
        progress = rememberDownloadProgress(pkg.id),
        onInstallClick = { runPackageInstallAction(pkg, action) },
        onUninstallClick = { runPackageUninstallAction(pkg, action) },
        onUpdateClick = { runPackageUpdateAction(pkg, action) },
        extraActions = { section?.ActionExtras(pkg, action) },
    )

    PackageUnavailableWarning(pkg)
}

@Composable
fun PackageStats(modifier: Modifier = Modifier, title: String, value: String, trailingVector: ImageVector? = null) {
    XedCard(modifier = modifier) {
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
            LeadingIconXedTab(
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
            when (PackageRoutes.entries[page]) {
                PackageRoutes.OVERVIEW -> MarkdownViewer(pkg.readmeUrl, refreshKey, onLoaded)
                PackageRoutes.REVIEWS -> ReviewsPage(pkg, refreshKey, onLoaded)
                PackageRoutes.CHANGELOG -> MarkdownViewer(pkg.changelogUrl, refreshKey, onLoaded)
            }
        }
    }
}
