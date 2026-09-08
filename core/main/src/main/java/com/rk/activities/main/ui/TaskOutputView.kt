package com.rk.activities.main.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.extension.api.Task
import com.rk.resources.drawables
import com.rk.resources.strings

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskOutputView(modifier: Modifier = Modifier) {
    val activeTask = TaskOutputState.activeTask ?: return
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

    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    val isAtBottom by remember {
        derivedStateOf {
            scrollState.canScrollForward
        }
    }

    LaunchedEffect(activeTask.output, expanded) {
        if (expanded && isAtBottom) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val floating = !imeVisible
    val corner = if (floating) 12.dp else 0.dp

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (floating) 8.dp else 0.dp,
                    vertical = if (floating) 8.dp else 0.dp,
                ),
        shape =
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = corner,
                bottomEnd = corner,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
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
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                }
            }

            Column(modifier = Modifier.fillMaxWidth().height(contentHeight)) {
                if (contentHeight > 0.dp) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = activeTask.output,
                            modifier =
                                Modifier.fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .horizontalScroll(hScrollState)
                                    .padding(8.dp),
                            style =
                                TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            softWrap = false,
                        )
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
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            task.icon()
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

            AnimatedContent(
                targetState =
                    task.latestLine.ifBlank {
                        if (task.isRunning) {
                            stringResource(strings.running)
                        } else {
                            stringResource(strings.finished)
                        }
                    },
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "latestLine",
            ) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
