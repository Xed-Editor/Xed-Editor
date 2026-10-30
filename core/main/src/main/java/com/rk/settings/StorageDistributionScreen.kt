package com.rk.settings

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.getCacheDir
import com.rk.file.getPrivateDir
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.file.themeDir
import com.rk.resources.strings
import com.rk.utils.formatFileSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

@Immutable
data class StorageComponent(
    val labelRes: Int,
    val color: Color,
    val size: Long = 0L,
)

private sealed interface StorageUiState {
    data object Loading : StorageUiState

    data class Loaded(val components: List<StorageComponent>, val total: Long) : StorageUiState
}

private enum class StorageCategory(val labelRes: Int, val color: Color) {
    TERMINAL(strings.ubuntu, Color(0xFFFF9800)),
    TERMINAL_HOME(strings.terminal_home, Color(0xFF4CAF50)),
    INDEX(strings.indexing_segment, Color(0xFF009688)),
    EXTENSIONS(strings.ext, Color(0xFF2196F3)),
    THEMES(strings.themes, Color(0xFF9C27B0)),
    ICON_PACKS(strings.icon_packs, Color(0xFFE91E63)),
    RUNNERS(strings.runners, Color(0xFF00BCD4)),
    CACHE(strings.cache, Color(0xFFFFC107)),
    OTHER(strings.other_files, Color(0xFF9E9E9E));

    fun toComponent(size: Long = 0L) = StorageComponent(labelRes, color, size)
}

@Composable
fun StorageDistributionScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<StorageUiState>(StorageUiState.Loading) }

    LaunchedEffect(Unit) {
        val (components, total) = calculateStorageDistribution(context)
        state = StorageUiState.Loaded(components, total)
    }

    PreferenceLayout(label = stringResource(strings.storage_distribution), backArrowVisible = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard(state)
            BreakdownCard(state)
        }
    }
}

@Composable
private fun SummaryCard(state: StorageUiState) {
    val loaded = state as? StorageUiState.Loaded
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor))) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(strings.total_storage_usage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = loaded?.let { formatFileSize(it.total) } ?: stringResource(strings.calculating),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            when (state) {
                StorageUiState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(16.dp).clip(CircleShape))
                }
                is StorageUiState.Loaded -> {
                    StorageBarGraph(state.components, state.total)
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(state: StorageUiState) {
    val loaded = state as? StorageUiState.Loaded

    val rows = remember(loaded) { loaded?.components ?: StorageCategory.entries.map { it.toComponent() } }

    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor))) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            rows.forEachIndexed { index, comp ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                StorageRow(comp = comp, isLoading = loaded == null)
            }
        }
    }
}

@Composable
private fun StorageRow(comp: StorageComponent, isLoading: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(comp.color))

            Spacer(Modifier.width(12.dp))

            Text(
                text = stringResource(comp.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isLoading) "—" else formatFileSize(comp.size),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Segmented bar with segments in its own rounded shape, separated by small gaps. Corner radii alternate between
 * segments (pill, softer, pill, softer, ...).
 *
 * Every segment gets a base width of [minSegmentWidth]. The remaining width is distributed proportionally to the
 * segment sizes, so small segments stay visible but still reflect their relative size.
 */
@Composable
private fun StorageBarGraph(
    components: List<StorageComponent>,
    totalSize: Long,
    modifier: Modifier = Modifier,
    minSegmentWidth: Dp = 8.dp,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }

    Canvas(modifier = modifier.fillMaxWidth().height(24.dp)) {
        val height = size.height
        val pill = height / 2f
        val soft = 6.dp.toPx()

        if (totalSize <= 0L) {
            drawRoundRect(color = trackColor, size = size, cornerRadius = CornerRadius(pill))
            return@Canvas
        }

        val gap = 3.dp.toPx()
        val visible = components.filter { it.size > 0L }
        if (visible.isEmpty()) return@Canvas

        val available = size.width - gap * (visible.size - 1)

        val baseWidth = minSegmentWidth.toPx().coerceAtMost(available / visible.size)
        val proportionalWidth = available - baseWidth * visible.size
        val visibleTotal = visible.sumOf { it.size }.toFloat()

        var x = 0f
        visible.forEachIndexed { index, comp ->
            val width = (baseWidth + comp.size / visibleTotal * proportionalWidth) * progress.value
            if (width > 0f) {
                val shape = if (index % 2 == 0) pill else soft
                val radius = shape.coerceAtMost(width / 2f)
                drawRoundRect(
                    color = comp.color,
                    topLeft = Offset(x, 0f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius),
                )
            }
            x += width + gap
        }
    }
}

private suspend fun calculateStorageDistribution(context: Context): Pair<List<StorageComponent>, Long> =
    withContext(Dispatchers.IO) {
        data class StorageLocationFilter(val file: File, val matcher: (FileObject) -> Boolean = { true }) {
            suspend fun sizeOrZero(): Long = runCatching {
                FileOperations.calculateMatchedContent(FileWrapper(file), matcher).totalSize
            }
                .getOrDefault(0L)
        }

        val appRoot = getPrivateDir(context)
        val cacheDir = getCacheDir(context)

        val dirs =
            linkedMapOf(
                StorageCategory.TERMINAL to
                    StorageLocationFilter(sandboxDir(context)) {
                        it.getAbsolutePath() != sandboxHomeDir(context).absolutePath
                    },
                StorageCategory.TERMINAL_HOME to StorageLocationFilter(sandboxHomeDir(context)),
                StorageCategory.INDEX to
                    StorageLocationFilter(appRoot.child("databases")) {
                        it.isDirectory() || it.getName().startsWith("index_database")
                    },
                StorageCategory.EXTENSIONS to StorageLocationFilter(localDir(context).child("extensions")),
                StorageCategory.THEMES to StorageLocationFilter(themeDir(context)),
                StorageCategory.ICON_PACKS to StorageLocationFilter(localDir(context).child("icon_pack")),
                StorageCategory.RUNNERS to StorageLocationFilter(localDir(context).child("runners")),
                StorageCategory.CACHE to StorageLocationFilter(cacheDir),
            )

        // Measure all dirs in parallel
        val (rawSizes, appRootSize) =
            coroutineScope {
                val categories = dirs.map { (cat, dir) -> async { cat to dir.sizeOrZero() } }
                val root = async { appRoot.sizeOrZero() }
                categories.awaitAll().toMap() to root.await()
            }

        // The cache dir is normally a child of the app root. Only add it if it lives elsewhere
        val total =
            if (appRoot.child("cache").canonicalPath == cacheDir.canonicalPath) appRootSize
            else appRootSize + rawSizes.getValue(StorageCategory.CACHE)
        val other = (total - rawSizes.values.sum()).coerceAtLeast(0L)

        val components =
            StorageCategory.entries.map { cat ->
                cat.toComponent(if (cat == StorageCategory.OTHER) other else rawSizes.getValue(cat))
            }

        components to total
    }

private suspend fun File.sizeOrZero(): Long = runCatching {
    FileOperations.calculateContent(FileWrapper(this)).totalSize
}
    .getOrDefault(0L)
