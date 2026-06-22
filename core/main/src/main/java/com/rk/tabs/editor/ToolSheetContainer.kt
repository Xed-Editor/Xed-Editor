package com.rk.tabs.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rk.components.XedDragHandle
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.theme.DesignTokens
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Stable
class ToolSheetState(
    val density: Density,
    val coroutineScope: CoroutineScope,
    minHeightDp: Dp,
    maxHeightDp: Dp,
    initialHeightDp: Dp,
) {
    var minHeightPx by mutableFloatStateOf(minHeightDp.value * density.density)
        private set
    var maxHeightPx by mutableFloatStateOf(maxHeightDp.value * density.density)
        private set

    var heightPx by mutableFloatStateOf(initialHeightDp.value * density.density)
        private set

    val heightDp: Dp
        get() = (heightPx / density.density).dp

    fun updateBounds(minPx: Float, maxPx: Float) {
        minHeightPx = minPx
        maxHeightPx = maxPx
        heightPx = heightPx.coerceIn(minPx, maxPx)
    }

    fun snapTo(px: Float) {
        heightPx = px.coerceIn(minHeightPx, maxHeightPx)
    }
}

@Composable
fun rememberToolSheetState(
    minHeight: Dp,
    initialHeight: Dp,
    maxHeight: Dp,
): ToolSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val state = remember {
        ToolSheetState(
            density = density,
            coroutineScope = coroutineScope,
            minHeightDp = minHeight,
            maxHeightDp = maxHeight,
            initialHeightDp = initialHeight,
        )
    }

    LaunchedEffect(minHeight, maxHeight) {
        val minPx = minHeight.value * density.density
        val maxPx = maxHeight.value * density.density
        state.updateBounds(minPx, maxPx)
    }

    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSheetContainer(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val imeHeightDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val isTablet = screenWidthDp >= 600.dp

    val availableHeight = (screenHeightDp - imeHeightDp - statusBarHeightDp).coerceAtLeast(360.dp)
    val maxHeight = (availableHeight * if (isTablet) 0.88f else 0.94f).coerceAtLeast(360.dp)
    val minHeight = DesignTokens.BottomSheet.minSheetHeight.coerceAtMost(maxHeight)
    val initialHeight = (availableHeight * if (isTablet) 0.60f else 0.55f).coerceIn(minHeight, maxHeight)

    val state = rememberToolSheetState(
        minHeight = minHeight,
        initialHeight = initialHeight,
        maxHeight = maxHeight,
    )

    ToolSheetContent(
        state = state,
        onDismissRequest = onDismissRequest,
        session = session,
        modifier = modifier,
        showTerminal = showTerminal,
        isTablet = isTablet,
        headerContent = headerContent,
        controls = controls,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun ToolSheetModalContainer(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val imeHeightDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val isTablet = screenWidthDp >= 600.dp

    val availableHeight = (screenHeightDp - imeHeightDp - statusBarHeightDp).coerceAtLeast(360.dp)
    val maxHeight = (availableHeight * if (isTablet) 0.88f else 0.94f).coerceAtLeast(360.dp)
    val minHeight = DesignTokens.BottomSheet.minSheetHeight.coerceAtMost(maxHeight)
    val initialHeight = (availableHeight * if (isTablet) 0.60f else 0.55f).coerceIn(minHeight, maxHeight)

    val state = rememberToolSheetState(
        minHeight = minHeight,
        initialHeight = initialHeight,
        maxHeight = maxHeight,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.BottomSheet.scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
    ) {
        ToolSheetContent(
            state = state,
            onDismissRequest = onDismissRequest,
            session = session,
            modifier = modifier,
            showTerminal = showTerminal,
            isTablet = isTablet,
            headerContent = headerContent,
            controls = controls,
            bottomBar = bottomBar,
            content = content,
        )
    }
}

@Composable
private fun ToolSheetContent(
    state: ToolSheetState,
    onDismissRequest: () -> Unit,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    isTablet: Boolean = false,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme

    val shape = DesignTokens.BottomSheet.shape

    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(state.heightDp)
            .shadow(
                elevation = DesignTokens.BottomSheet.elevation,
                shape = shape,
                clip = true,
            )
            .background(colorScheme.surfaceContainer, shape = shape)
            .border(
                width = 0.5.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            state.snapTo(state.heightPx - delta)
                        },
                        onDragStarted = { isDragging = true },
                        onDragStopped = { velocity ->
                            isDragging = false
                            val current = state.heightPx
                            val midPx = (state.minHeightPx + state.maxHeightPx) / 2f
                            if (velocity.absoluteValue > state.density.density * 2f) {
                                state.snapTo(if (velocity < 0) state.maxHeightPx else state.minHeightPx)
                            } else {
                                state.snapTo(if (current < midPx) state.minHeightPx else state.maxHeightPx)
                            }
                        },
                    )
                    .background(colorScheme.surfaceContainer)
            ) {
                XedDragHandle(
                    isDragging = isDragging,
                    modifier = Modifier.clickable {
                        val midPx = (state.minHeightPx + state.maxHeightPx) / 2f
                        state.snapTo(if (state.heightPx < midPx) state.maxHeightPx else state.minHeightPx)
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        headerContent?.invoke()
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        controls?.invoke(this)

                        Spacer(Modifier.width(4.dp))

                        FilledIconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(28.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            XedIcon(
                                com.rk.icons.Icon.DrawableRes(drawables.close),
                                contentDescription = "Close",
                                modifier = Modifier.size(13.dp),
                                tint = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.15f),
                    thickness = 0.5.dp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colorScheme.surfaceContainerLow),
            ) {
                if (showTerminal) {
                    SheetTerminal(
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                        showKeys = true,
                    )
                } else {
                    content?.invoke()
                }
            }

            bottomBar?.let {
                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                    thickness = 0.5.dp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceContainer)
                        .navigationBarsPadding(),
                ) {
                    it()
                }
            }
        }
    }
}
