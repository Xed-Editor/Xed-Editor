package com.rk.tabs.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    private val animatableHeight = Animatable(initialHeightDp.value * density.density)

    val heightPx: Float
        get() = animatableHeight.value

    val heightDp: Dp
        get() = (animatableHeight.value / density.density).dp

    suspend fun updateBounds(minPx: Float, maxPx: Float) {
        minHeightPx = minPx
        maxHeightPx = maxPx
        val target = animatableHeight.value.coerceIn(minPx, maxPx)
        if (target != animatableHeight.value) {
            animatableHeight.snapTo(target)
        }
    }

    suspend fun snapTo(px: Float) {
        animatableHeight.snapTo(px.coerceIn(minHeightPx, maxHeightPx))
    }

    suspend fun animateTo(px: Float, initialVelocity: Float = 0f) {
        animatableHeight.animateTo(
            targetValue = px.coerceIn(minHeightPx, maxHeightPx),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialVelocity = initialVelocity
        )
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
    val navBarHeightDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val isTablet = screenWidthDp >= 600.dp

    val availableHeight = screenHeightDp - imeHeightDp - navBarHeightDp - statusBarHeightDp
    val maxHeight = (availableHeight * 0.92f).coerceAtLeast(300.dp)
    val minHeight = 200.dp.coerceAtMost(maxHeight)
    val initialHeight = (availableHeight * 0.55f).coerceIn(minHeight, maxHeight)

    val state = rememberToolSheetState(
        minHeight = minHeight,
        initialHeight = initialHeight,
        maxHeight = maxHeight,
    )

    ToolSheetContent(
        state = state,
        onDismissRequest = onDismissRequest,
        cwd = cwd,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val imeHeightDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBarHeightDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val isTablet = screenWidthDp >= 600.dp

    val availableHeight = screenHeightDp - imeHeightDp - navBarHeightDp - statusBarHeightDp
    val maxHeight = (availableHeight * 0.92f).coerceAtLeast(300.dp)
    val minHeight = 200.dp.coerceAtMost(maxHeight)
    val initialHeight = (availableHeight * 0.55f).coerceIn(minHeight, maxHeight)

    val state = rememberToolSheetState(
        minHeight = minHeight,
        initialHeight = initialHeight,
        maxHeight = maxHeight,
    )

    LaunchedEffect(Unit) {
        runCatching { sheetState.expand() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = Color.Transparent,
    ) {
        ToolSheetContent(
            state = state,
            onDismissRequest = onDismissRequest,
            cwd = cwd,
            session = session,
            modifier = Modifier.fillMaxWidth(),
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
    cwd: String,
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
    val coroutineScope = rememberCoroutineScope()

    val shape = if (isTablet) DesignTokens.BottomSheet.shapeTablet else DesignTokens.BottomSheet.shape

    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isTablet) {
                    Modifier.padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                } else {
                    Modifier
                }
            )
            .height(state.heightDp)
            .shadow(
                elevation = if (isTablet) DesignTokens.BottomSheet.elevationTablet else DesignTokens.BottomSheet.elevation,
                shape = shape,
                clip = true,
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.surfaceContainerHigh,
                        colorScheme.surfaceContainerLow,
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 0.5.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.15f),
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    if (isTablet) WindowInsets(0.dp) else WindowInsets.navigationBars
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            coroutineScope.launch {
                                state.snapTo(state.heightPx - delta)
                            }
                        },
                        onDragStarted = { isDragging = true },
                        onDragStopped = { velocity ->
                            isDragging = false
                            val minPx = state.minHeightPx
                            val maxPx = state.maxHeightPx
                            val midPx = (minPx + maxPx) / 2f

                            val heightVelocity = -velocity
                            coroutineScope.launch {
                                val targetPx = when {
                                    heightVelocity > 1200f -> maxPx
                                    heightVelocity < -1200f -> minPx
                                    else -> {
                                        val current = state.heightPx
                                        val distMin = (current - minPx).absoluteValue
                                        val distMid = (current - midPx).absoluteValue
                                        val distMax = (current - maxPx).absoluteValue

                                        if (distMin < distMid && distMin < distMax) minPx
                                        else if (distMax < distMin && distMax < distMid) maxPx
                                        else midPx
                                    }
                                }
                                state.animateTo(targetPx, heightVelocity)
                            }
                        },
                    )
                    .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            ) {
                XedDragHandle(isDragging = isDragging)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
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
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            XedIcon(
                                com.rk.icons.Icon.DrawableRes(drawables.close),
                                contentDescription = "Close",
                                modifier = Modifier.size(16.dp),
                                tint = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                    thickness = 0.5.dp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colorScheme.surfaceContainerLowest),
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
                    color = colorScheme.outlineVariant.copy(alpha = 0.1f),
                    thickness = 0.5.dp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceContainerLow)
                ) {
                    it()
                }
            }
        }
    }
}


