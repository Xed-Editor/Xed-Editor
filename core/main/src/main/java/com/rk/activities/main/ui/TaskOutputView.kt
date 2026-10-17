package com.rk.activities.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rk.App.Companion.themeManager
import com.rk.activities.main.MainActivity
import com.rk.extension.api.Task
import com.rk.extension.api.TaskRegistry
import com.rk.resources.drawables
import com.rk.resources.strings
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskOutputView(modifier: Modifier = Modifier) {
    val activeTask = TaskOutputState.focusedTask ?: return
    val expanded = TaskOutputState.expanded
    val imeVisible = WindowInsets.isImeVisible

    val density = LocalDensity.current
    val heightPx = LocalWindowInfo.current.containerSize.height
    val expandedHeight = with(density) { (heightPx * 0.5f).toDp() }
    val contentHeight by
        animateDpAsState(
            targetValue = if (expanded) expandedHeight else 0.dp,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
            label = "taskOutputHeight",
        )

    val props =
        if (isSystemInDarkTheme()) {
            themeManager.currentTheme.darkTerminalColors
        } else {
            themeManager.currentTheme.lightTerminalColors
        }

    val lines = activeTask.output
    val listState = rememberLazyListState()
    val hScrollState = rememberScrollState()

    var follow by remember { mutableStateOf(true) }

    // Only the user's own scrolling changes follow mode (at bottom = follow, scrolled up = stop)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canForward) -> if (scrolling) follow = !canForward }
    }

    // Stick to the bottom while new lines arrive
    LaunchedEffect(lines.size) { if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }

    val floating = !imeVisible
    val corner = if (floating) 12.dp else 0.dp

    val currentTab = MainActivity.instance?.viewModel?.currentVisibleTab
    val bottomPadding = currentTab?.floatPadding ?: 0.dp

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (floating) 8.dp else 0.dp,
                    vertical = bottomPadding + (if (floating) 8.dp else 0.dp),
                ),
        shape =
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = corner,
                bottomEnd = corner,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (floating) 4.dp else 0.dp,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = {
                            if (total < -40f) TaskOutputState.expanded = true
                            else if (total > 40f) TaskOutputState.expanded = false
                        },
                    )
                }
        ) {
            Column(
                modifier =
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer).clickable {
                        TaskOutputState.expanded = !TaskOutputState.expanded
                    }
            ) {
                // Drag handle
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.width(32.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )
                }

                Header(activeTask, expanded)

                if (activeTask.isRunning) {
                    val progress = activeTask.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().height(contentHeight)) {
                if (contentHeight > 0.dp) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val textStyle =
                            TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                        val textMeasurer = rememberTextMeasurer()
                        val charWidthPx =
                            remember(textMeasurer) { textMeasurer.measure("0".repeat(20), textStyle).size.width / 20f }

                        val maxColumns by remember(lines) { derivedStateOf { lines.maxOfOrNull { it.columns } ?: 0 } }
                        val contentWidth = with(LocalDensity.current) { ((maxColumns + 1) * charWidthPx).toDp() }

                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().horizontalScroll(hScrollState),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(8.dp),
                            ) {
                                items(count = lines.size) { index ->
                                    val line = lines.getOrNull(index) ?: return@items
                                    Text(
                                        text = line.toAnnotatedString(props),
                                        modifier = Modifier.width(contentWidth),
                                        minLines = 1, // keeps empty lines at full height
                                        softWrap = false,
                                        style = textStyle,
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                            AnimatedVisibility(
                                visible = !follow,
                                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                                exit = fadeOut() + scaleOut(targetScale = 0.6f),
                            ) {
                                val coroutineScope = rememberCoroutineScope()

                                SmallFloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            follow = true
                                            listState.scrollToItem(lines.lastIndex)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Icon(
                                        painter = painterResource(drawables.chevron_down),
                                        contentDescription = stringResource(strings.scroll_to_bottom),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(task: Task, expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tasks = TaskRegistry.tasks.collectAsStateWithLifecycle().value

        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (tasks.size > 1) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(tasks.size.toString())
                        }
                    }
                ) {
                    task.icon()
                }
            } else {
                task.icon()
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    task.latestLine.ifBlank {
                        if (task.isRunning) {
                            stringResource(strings.running)
                        } else {
                            stringResource(strings.finished)
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (task.isRunning) {
            IconButton(onClick = { task.stop() }) {
                Icon(
                    painter = painterResource(drawables.stop),
                    contentDescription = stringResource(strings.stop),
                )
            }
        } else {
            IconButton(onClick = { task.dismiss() }) {
                Icon(
                    painter = painterResource(drawables.close),
                    contentDescription = stringResource(strings.close),
                )
            }
        }

        IconButton(onClick = { TaskOutputState.expanded = !expanded }) {
            Icon(
                painter =
                    if (expanded) {
                        painterResource(drawables.chevron_down)
                    } else {
                        painterResource(drawables.chevron_up)
                    },
                contentDescription =
                    if (expanded) {
                        stringResource(strings.collapse)
                    } else {
                        stringResource(strings.expand)
                    },
            )
        }
    }
}
