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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App.Companion.extensionManager
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayout
import com.rk.extension.Extension
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
fun ExtensionDetail(extension: Extension?) {
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    RefreshablePreferenceLayout(
        label = extension?.name ?: stringResource(strings.ext_not_found),
        backArrowVisible = true,
        isExpandedScreen = true,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshKey++
        },
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
            TabSection(extension, scope, refreshKey) { isRefreshing = false }
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

    var showSourceCodeSheet by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(extension.iconUrl)
                    .fallback(drawables.extension)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).padding(end = 16.dp),
            contentDescription = null,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extension.name,
                style = Typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionAuthorIcon(extension.author, Modifier.size(16.dp).padding(end = 4.dp))
                Text(
                    text = "${extension.author} • v${extension.version}",
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = { showSourceCodeSheet = true }) {
            Icon(painter = painterResource(drawables.xml), contentDescription = null)
        }

        ExtensionActionButton(
            extension = extension,
            installState = installState,
            scope = scope,
            onInstallClick = { runExtensionInstallAction(extension, updateInstallState, scope, context, activity) },
            onUninstallClick = { runExtensionUninstallAction(extension, updateInstallState, activity) },
        )
    }

    val size by produceState("---") { value = formatFileSize(extension.calcSize()) }
    val rating by
        produceState<Pair<String, ImageVector?>>("---" to null) {
            val rating = extension.getRating() ?: return@produceState
            value = rating.toString() to Icons.Default.Star
        }
    val downloadCount by
        produceState("---") {
            val count = extension.getDownloadCount() ?: return@produceState
            value = formatNumberCompact(count)
        }
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExtensionStats(Modifier.weight(1f), stringResource(strings.downloads).uppercase(), downloadCount)
        ExtensionStats(Modifier.weight(1f), stringResource(strings.rating).uppercase(), rating.first, rating.second)
        ExtensionStats(Modifier.weight(1f), stringResource(strings.size).uppercase(), size)
    }

    if (showSourceCodeSheet) {
        SourceCodeSheet(extension) { showSourceCodeSheet = false }
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
    REVIEWS(Icon.DrawableRes(drawables.comment), strings.reviews.getString(), "reviews"),
    CHANGELOG(Icon.DrawableRes(drawables.update), strings.changelog.getString(), "changelog"),
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
