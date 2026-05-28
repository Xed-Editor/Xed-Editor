package com.rk.tabs.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.launch

private data class SheetUiState(
    val sheetHeight: Dp,
    val onDragStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragEndForInline: () -> Unit,
    val onDragEndForModal: (() -> Unit)? = null,
)

@Composable
private fun rememberSheetUiState(
    minHeight: Dp = 320.dp,
    initialHeight: Dp = 560.dp,
    onModalMinimize: (() -> Unit)? = null,
    onModalExpand: (() -> Unit)? = null,
): SheetUiState {
    val density = LocalDensity.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp
    var sheetHeight by remember { mutableStateOf(initialHeight.coerceIn(minHeight, maxHeight)) }
    var handleDrag by remember { mutableStateOf(0f) }

    return SheetUiState(
        sheetHeight = sheetHeight,
        onDragStart = { handleDrag = 0f },
        onDrag = { dragAmount ->
            handleDrag += dragAmount
            sheetHeight = (sheetHeight - with(density) { dragAmount.toDp() }).coerceIn(minHeight, maxHeight)
        },
        onDragEndForInline = {},
        onDragEndForModal = {
            when {
                handleDrag > 60f -> onModalMinimize?.invoke()
                handleDrag < -60f -> onModalExpand?.invoke()
            }
        },
    )
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
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ui = rememberSheetUiState()

    ToolSheetContent(
        onDismissRequest = onDismissRequest,
        cwd = cwd,
        session = session,
        modifier = modifier,
        sheetHeight = ui.sheetHeight,
        showTerminal = showTerminal,
        headerContent = headerContent,
        controls = controls,
        onDragStart = ui.onDragStart,
        onDrag = ui.onDrag,
        onDragEnd = ui.onDragEndForInline,
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
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val ui = rememberSheetUiState(
        onModalMinimize = { scope.launch { runCatching { sheetState.partialExpand() } } },
        onModalExpand = { scope.launch { sheetState.expand() } },
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
            onDismissRequest = onDismissRequest,
            cwd = cwd,
            session = session,
            modifier = Modifier.fillMaxWidth(),
            sheetHeight = ui.sheetHeight,
            showTerminal = showTerminal,
            headerContent = headerContent,
            controls = controls,
            onDragStart = ui.onDragStart,
            onDrag = ui.onDrag,
            onDragEnd = { ui.onDragEndForModal?.invoke() },
            bottomBar = bottomBar,
            content = content,
        )
    }
}

@Composable
private fun ToolSheetContent(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    sheetHeight: Dp = 595.dp,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .shadow(8.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.surfaceContainerHigh,
                        colorScheme.surfaceContainerHighest,
                    )
                ),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .border(
                0.5.dp,
                colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left & Center: Tabs + Drag Handle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { onDragStart() },
                                onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                                onDragEnd = onDragEnd,
                            )
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    headerContent?.invoke()

                    // Drag Handle Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }

                // Right: Controls + Close
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    controls?.invoke(this)
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
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

            // Main Content Area
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (showTerminal) {
                    SheetTerminal(session = session, modifier = Modifier.fillMaxSize(), showKeys = true)
                } else {
                    content?.invoke(this@Column)
                }
            }

            // Bottom Bar
            bottomBar?.let {
                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.15f),
                    thickness = 0.5.dp,
                )
                it()
            }
        }
    }
}
