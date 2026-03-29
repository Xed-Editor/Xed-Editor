package com.rk.settings.extension

import android.graphics.Typeface
import android.text.Spanned
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
            TabSection(extension, scope)
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
            Text(text = extension.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Text(
                text = "by ${extension.authors.joinToString()} • v${extension.version}",
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
private fun TabSection(extension: Extension, scope: CoroutineScope) {
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
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (ExtensionRoutes.entries[page]) {
                ExtensionRoutes.OVERVIEW -> MarkdownViewer(url = extension.readmeUrl)
                ExtensionRoutes.CONTRIBUTORS -> ContributorsTab(extension)
                ExtensionRoutes.CHANGELOG -> MarkdownViewer(url = extension.changelogUrl)
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
fun MarkdownViewer(url: String, modifier: Modifier = Modifier) {
    var state by remember(url) { mutableStateOf<MarkdownStatus>(MarkdownStatus.Loading) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val client = remember { OkHttpClient() }

    LaunchedEffect(url) {
        state = MarkdownStatus.Loading
        state = withContext(Dispatchers.IO) { loadMarkdown(url, primaryColor.toArgb(), client) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            MarkdownStatus.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is MarkdownStatus.Error -> {
                val markdownStatus = state as MarkdownStatus.Error
                val color =
                    when (markdownStatus) {
                        is MarkdownStatus.Error.Empty -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                StateScreen(
                    painter = painterResource(markdownStatus.drawableRes),
                    text = stringResource(markdownStatus.stringRes),
                    color = color,
                )
            }
            is MarkdownStatus.Success -> {
                val markdownStatus = state as MarkdownStatus.Success
                AndroidView(
                    factory = { ctx -> TextView(ctx) },
                    update = { it.text = markdownStatus.spanned },
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
        Icon(painter = painter, contentDescription = null, modifier = Modifier.size(64.dp), tint = color)
        Text(text = text, color = color, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
    }
}

private suspend fun loadMarkdown(url: String, primaryColor: Int, client: OkHttpClient): MarkdownStatus {
    return runCatching {
            val markdown =
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return when (response.code) {
                                404 -> MarkdownStatus.Error.Empty
                                else -> MarkdownStatus.Error.Unknown
                            }
                        }
                        response.body.string()
                    }
                } else {
                    val file = File(url)
                    if (!file.exists()) {
                        return MarkdownStatus.Error.Empty
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

@Composable
fun ContributorsTab(extension: Extension) {
    Text(text = extension.authors.joinToString())
}
