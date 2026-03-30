package com.rk.settings.extension

import android.content.Intent
import android.graphics.Typeface
import android.text.Spanned
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App.Companion.extensionManager
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayout
import com.rk.extension.Extension
import com.rk.extension.ExtensionAuthor
import com.rk.extension.Review
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.Typography
import com.rk.utils.formatFileSize
import com.rk.utils.formatNumberCompact
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import java.io.File
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
            Icon(painter = painterResource(drawables.ic_language_xml), contentDescription = null)
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

enum class SourceCodeProvider(val drawableRes: Int, val viewStringRes: Int) {
    GitHub(drawables.github, strings.view_github),
    GitLab(drawables.gitlab, strings.view_gitlab),
    BitBucket(drawables.bitbucket, strings.view_bitbucket),
    Other(drawables.ic_language_xml, strings.view_repo);

    companion object {
        fun fromUrl(url: String): SourceCodeProvider {
            val hostName = URL(url).host
            return when (hostName) {
                "github.com" -> GitHub
                "gitlab.com" -> GitLab
                "bitbucket.org" -> BitBucket
                else -> Other
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceCodeSheet(extension: Extension, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val sourceCodeProvider = SourceCodeProvider.fromUrl(extension.repository)

    ModalBottomSheet(onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    painter = painterResource(drawables.ic_language_xml),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )

                Text(
                    text = stringResource(strings.source_code),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val sheetDescription =
                extension.license?.let { stringResource(strings.ext_source_desc_license).fillPlaceholders(it) }
                    ?: stringResource(strings.ext_source_desc)
            Text(sheetDescription, modifier = Modifier.padding(horizontal = 16.dp))

            PreferenceGroup {
                SettingsToggle(
                    label = stringResource(sourceCodeProvider.viewStringRes),
                    description = extension.repository,
                    isEnabled = true,
                    showSwitch = false,
                    default = false,
                    startWidget = {
                        Icon(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            painter = painterResource(sourceCodeProvider.drawableRes),
                            contentDescription = null,
                        )
                    },
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            painter = painterResource(drawables.open_in_new),
                            contentDescription = null,
                        )
                    },
                    sideEffect = {
                        val intent = Intent(Intent.ACTION_VIEW, extension.repository.toUri())
                        context.startActivity(intent)
                    },
                )
            }
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
                ExtensionRoutes.REVIEWS -> ReviewsTab(extension, refreshKey, onLoaded)
                ExtensionRoutes.CHANGELOG -> MarkdownViewer(extension.changelogUrl, refreshKey, onLoaded)
            }
        }
    }
}

sealed interface MarkdownStatus {
    object Loading : MarkdownStatus

    sealed class Error(val stringRes: Int, val drawableRes: Int) : MarkdownStatus {
        object Network : Error(strings.network_err, drawables.cloud_off)

        object Unknown : Error(strings.unknown_err, drawables.error)

        object Empty : Error(strings.empty_err, drawables.file)
    }

    data class Success(val spanned: Spanned) : MarkdownStatus
}

@Composable
fun MarkdownViewer(url: String, refreshKey: Int, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember(url) { mutableStateOf<MarkdownStatus>(MarkdownStatus.Loading) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val client = remember { OkHttpClient() }

    LaunchedEffect(url, refreshKey) {
        state = MarkdownStatus.Loading
        state = loadMarkdown(url, primaryColor.toArgb(), client)
        onLoaded()
    }

    AnimatedContent(targetState = state, modifier = modifier.fillMaxWidth()) { state ->
        when (state) {
            MarkdownStatus.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MarkdownStatus.Error -> {
                val color =
                    when (state) {
                        is MarkdownStatus.Error.Empty -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                StateScreen(
                    painter = painterResource(state.drawableRes),
                    text = stringResource(state.stringRes),
                    color = color,
                )
            }
            is MarkdownStatus.Success -> {
                AndroidView(
                    factory = { ctx -> TextView(ctx) },
                    update = { it.text = state.spanned },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun StateScreen(painter: Painter, text: String, color: Color = LocalContentColor.current) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painter = painter, contentDescription = null, modifier = Modifier.size(48.dp), tint = color)
        Text(text = text, color = color, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
    }
}

private suspend fun loadMarkdown(url: String, primaryColor: Int, client: OkHttpClient): MarkdownStatus {
    return withContext(Dispatchers.IO) {
        runCatching {
                val markdown =
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        val request = Request.Builder().url(url).build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                return@withContext when (response.code) {
                                    404 -> MarkdownStatus.Error.Empty
                                    else -> MarkdownStatus.Error.Unknown
                                }
                            }
                            response.body.string()
                        }
                    } else {
                        val file = File(url)
                        if (!file.exists()) {
                            return@withContext MarkdownStatus.Error.Empty
                        }
                        file.readText()
                    }

                val spanned =
                    SimpleMarkdownRenderer.renderAsync(
                        markdown,
                        boldColor = primaryColor,
                        inlineCodeColor = primaryColor,
                        codeTypeface = Typeface.MONOSPACE,
                        linkColor = primaryColor,
                    )

                MarkdownStatus.Success(spanned)
            }
            .getOrElse {
                it.printStackTrace()
                MarkdownStatus.Error.Network
            }
    }
}

@Composable
fun ExtensionAuthorIcon(author: ExtensionAuthor, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(author.github?.let { "https://github.com/$it.png" })
                .fallback(drawables.person)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
    )
}

sealed interface ReviewsStatus {
    object Loading : ReviewsStatus

    sealed class Error(val stringRes: Int, val drawableRes: Int) : ReviewsStatus {
        object Network : Error(strings.network_err, drawables.cloud_off)

        object Unknown : Error(strings.unknown_err, drawables.error)

        object NotSupported : Error(strings.reviews_not_supported, drawables.comment)
    }

    data class Success(val reviews: List<Review>) : ReviewsStatus
}

@Composable
fun ReviewsTab(extension: Extension, refreshKey: Int, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember(extension) { mutableStateOf<ReviewsStatus>(ReviewsStatus.Loading) }

    LaunchedEffect(extension, refreshKey) {
        state = ReviewsStatus.Loading
        // TODO: Implement
        state = ReviewsStatus.Error.NotSupported
        onLoaded()
    }

    AnimatedContent(targetState = state, modifier = modifier.fillMaxWidth()) { state ->
        when (state) {
            ReviewsStatus.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ReviewsStatus.Error -> {
                val color =
                    when (state) {
                        is ReviewsStatus.Error.NotSupported -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                StateScreen(
                    painter = painterResource(state.drawableRes),
                    text = stringResource(state.stringRes),
                    color = color,
                )
            }
            is ReviewsStatus.Success -> {}
        }
    }
}
