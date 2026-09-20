package com.rk.ai.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.icons.ArrowUpward
import com.rk.ai.icons.ContentCopy
import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiChatRole
import com.rk.ai.model.PendingApproval
import com.rk.ai.model.PendingQuestion
import com.rk.ai.model.ToolCallStatus
import com.rk.ai.settings.AiSettings
import com.rk.ai.settings.PermissionMode
import com.rk.ai.tools.ToolBodyPart
import com.rk.ai.tools.toolCallView
import com.rk.theme.greenStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun AiChatScreen(controller: AiChatController, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages = controller.messages

    // Slack for "the end is on screen": a streamed message can be a couple of pixels short for a frame.
    val endTolerance = with(LocalDensity.current) { 4.dp.roundToPx() }

    // How far above the end the reader must be before the jump-to-latest button shows.
    val jumpThreshold = with(LocalDensity.current) { 72.dp.roundToPx() }

    // Cannot drive following on its own: it goes false as soon as new text is laid out below the fold.
    val atEnd by
        remember(endTolerance) {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last == null ||
                    (last.index == info.totalItemsCount - 1 &&
                        last.offset + last.size <= info.viewportEndOffset + endTolerance)
            }
        }

    // Hysteresis for the button: only true once the reader is meaningfully above the end.
    val scrolledFromEnd by
        remember(jumpThreshold) {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last == null ||
                    last.index != info.totalItemsCount - 1 ||
                    last.offset + last.size > info.viewportEndOffset + jumpThreshold
            }
        }

    // Switched off while the user drags, so auto-scroll never fights a reader who scrolled up.
    var autoFollow by remember { mutableStateOf(true) }

    // Non-animated on purpose: an `animateScrollToItem` retargeted on every streamed token made the
    // growing text flicker. `requestScrollToItem` is applied during the next layout pass instead.
    fun followEnd() {
        if (autoFollow && messages.isNotEmpty()) {
            // `Int.MAX_VALUE` targets the end of the last item; a message taller than the viewport
            // would otherwise scroll to its top.
            listState.requestScrollToItem(messages.lastIndex, Int.MAX_VALUE)
        }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> autoFollow = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> if (atEnd) autoFollow = true
            }
        }
    }

    // Covers a fling that reaches the end after the drag gesture stopped.
    LaunchedEffect(listState) {
        snapshotFlow { atEnd }.collect { if (it) autoFollow = true }
    }

    // Follow the stream: the last text, the turn count and the panels that change the viewport height.
    LaunchedEffect(listState) {
        snapshotFlow {
                val last = controller.messages.lastOrNull()
                listOf<Any?>(
                    last?.text,
                    controller.messages.size,
                    controller.pendingApproval,
                    controller.pendingQuestion,
                )
            }
            .collect { followEnd() }
    }

    // Follow the layout too: markdown parses off the main thread, so a message can grow after its
    // last token. Only the bottom item's measured size is observed, so re-pinning cannot feed back.
    LaunchedEffect(listState) {
        snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index to it.size }
            }
            .collect { followEnd() }
    }

    Column(modifier.fillMaxSize()) {
        // Kept out of the scrolling transcript so the goal stays visible.
        controller.goal?.let { goal ->
            GoalBanner(goal = goal, onClear = controller::clearGoal)
        }

        // The button is anchored to the transcript area; a fixed screen offset would be covered by
        // the approval card.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyState(onSuggestion = controller::send, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        when (message.role) {
                            AiChatRole.Tool -> AiToolStep(message)
                            AiChatRole.User -> UserMessage(message)
                            AiChatRole.Assistant ->
                                AssistantMessage(
                                    message = message,
                                    reasoningExpanded = controller.showReasoning,
                                    onReasoningToggled = controller::setShowReasoning,
                                )
                        }
                    }
                }
            }

            if (messages.isNotEmpty() && scrolledFromEnd) {
                SmallFloatingActionButton(
                    onClick = {
                        autoFollow = true
                        scope.launch {
                            listState.animateScrollToItem(messages.lastIndex, Int.MAX_VALUE)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to latest")
                }
            }
        }

        controller.pendingQuestion?.let { question ->
            QuestionCard(question = question, onAnswer = controller::answerQuestion)
        }

        controller.pendingApproval?.let { approval ->
            ApprovalCard(approval = approval, onDecision = controller::approve)
        }

        controller.lastError?.let { ErrorBanner(it) }

        Composer(
            value = controller.draft,
            onValueChange = controller::setDraft,
            running = controller.isRunning,
            onSend = { controller.send(controller.draft) },
            onStop = controller::stop,
        )
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Xed AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask about this project. I can read files, edit them, search the code and run shell commands.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        SUGGESTIONS.forEachIndexed { index, suggestion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSuggestion(suggestion.prompt) }
                        .padding(vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        suggestion.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (index != SUGGESTIONS.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "${AiSettings.modelId} · ${AiSettings.currentPermissionMode().label()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserMessage(message: AiChatMessage) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(12.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    message: AiChatMessage,
    reasoningExpanded: Boolean,
    onReasoningToggled: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (message.reasoning.isNotBlank()) {
            ThinkingBlock(
                reasoning = message.reasoning,
                streaming = message.isStreaming && message.text.isEmpty(),
                expanded = reasoningExpanded,
                onToggled = onReasoningToggled,
            )
            if (message.text.isNotEmpty()) Spacer(Modifier.height(10.dp))
        }

        when {
            // Nothing to show yet: the thinking block already has the spinner.
            message.text.isEmpty() && message.reasoning.isNotBlank() -> Unit
            message.text.isEmpty() && message.isStreaming -> TypingDots()
            // Parsed while streaming: `AiMarkdownText` retains the previous result, so it grows in place.
            message.text.isNotEmpty() ->
                SelectionContainer {
                    AiMarkdownText(
                        markdown = message.text,
                    )
                }
        }

        if (message.isStreaming && message.text.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            StreamingCursor()
        }

        message.error?.let { error ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The model's reasoning, collapsed by default. */
@Composable
private fun ThinkingBlock(
    reasoning: String,
    streaming: Boolean,
    expanded: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    // `KeyboardArrowDown` points down at rest; -90f while collapsed points it right.
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "thinkingChevron")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { onToggled(!expanded) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide thinking" else "Show thinking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp).rotate(rotation),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (streaming) "Thinking…" else "Thought process",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (streaming) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectionContainer {
                    Text(
                        text = reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by
                transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = index * 180),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "dot$index",
                )
            Box(
                Modifier.padding(horizontal = 2.dp)
                    .size(7.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 500),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "cursorAlpha",
        )
    Box(
        Modifier.size(width = 8.dp, height = 14.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun AiToolStep(message: AiChatMessage, nested: Boolean = false) {
    val status = message.toolStatus ?: ToolCallStatus.Running
    // Parsed once per call: the model's raw JSON becomes an action name, a target and labels.
    val view = remember(message.toolName, message.toolArgs) {
        toolCallView(message.toolName.orEmpty(), message.toolArgs)
    }
    val diff = message.diff?.takeIf { it.isNotBlank() }
    val result = message.text.takeIf { it.isNotBlank() }
    // A sub-agent's final answer is repeated as the result, so only intermediate steps are children.
    val steps = message.children.dropLastWhile { it.role == AiChatRole.Assistant }
    val hasBody = view.arguments.isNotEmpty() || diff != null || result != null || steps.isNotEmpty()

    var expanded by remember { mutableStateOf(false) }
    // `KeyboardArrowDown` points down at rest: -90f while collapsed points it right.
    val rotation by animateFloatAsState(if (expanded && hasBody) 0f else -90f, label = "chevron")

    // Open the card on the sub-agent's first step, but only once: a manual collapse must stick.
    var autoExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(status, steps.size) {
        if (!autoExpanded && status == ToolCallStatus.Running && steps.isNotEmpty()) {
            autoExpanded = true
            expanded = true
        }
    }

    Surface(
        color =
            if (nested) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // The identifying value (path, command, query or URL) keeps a collapsed card informative.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(enabled = hasBody) { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).rotate(rotation),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = view.action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (view.target != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = view.target,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (status.isInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                StatusLabel(status)
            }

            if (expanded && hasBody) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (view.arguments.isNotEmpty()) ToolArguments(view.arguments)
                    if (steps.isNotEmpty()) SubAgentSteps(steps)
                    diff?.let { AiDiffView(it) }
                    result?.let {
                        CodePanel(
                            title =
                                if (status == ToolCallStatus.Failed || status == ToolCallStatus.Denied) {
                                    "error"
                                } else {
                                    "result"
                                },
                            body = it.take(6000),
                        )
                    }
                }
            } else if (diff != null) {
                // Surface the file change without expanding the card.
                Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) { AiDiffView(diff) }
            }
        }
    }
}

/** A sub-agent's intermediate steps, rendered inside the launching call's card. */
@Composable
private fun SubAgentSteps(steps: List<AiChatMessage>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEach { step ->
            when (step.role) {
                AiChatRole.Assistant ->
                    if (step.text.isNotBlank()) {
                        SelectionContainer { AiMarkdownText(step.text) }
                    }
                AiChatRole.Tool -> AiToolStep(step, nested = true)
                AiChatRole.User -> Unit
            }
        }
    }
}

@Composable
private fun ToolArguments(parts: List<ToolBodyPart>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            when (part) {
                is ToolBodyPart.Field -> ToolFieldRow(part.label, part.value)
                is ToolBodyPart.Block -> CodePanel(title = part.label, body = part.code)
            }
        }
    }
}

@Composable
private fun ToolFieldRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp).padding(top = 2.dp),
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CopyButton(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MILLIS)
            copied = false
        }
    }

    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            imageVector = if (copied) Icons.Filled.Check else ContentCopy,
            contentDescription = if (copied) "Copied" else "Copy",
            tint =
                if (copied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun StatusLabel(status: ToolCallStatus) {
    val label: String
    val color: Color
    when (status) {
        ToolCallStatus.AwaitingApproval -> {
            label = "awaiting approval"
            color = MaterialTheme.colorScheme.tertiary
        }
        ToolCallStatus.AwaitingInput -> {
            label = "waiting for you"
            color = MaterialTheme.colorScheme.tertiary
        }
        ToolCallStatus.Running -> {
            label = "running"
            color = MaterialTheme.colorScheme.primary
        }
        ToolCallStatus.Success -> {
            label = "done"
            color = MaterialTheme.colorScheme.greenStatus
        }
        ToolCallStatus.Failed -> {
            label = "failed"
            color = MaterialTheme.colorScheme.error
        }
        ToolCallStatus.Denied -> {
            label = "denied"
            color = MaterialTheme.colorScheme.error
        }
        ToolCallStatus.Interrupted -> {
            // A run that did not survive the session: stopped rather than failed.
            label = "stopped"
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CodePanel(title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CopyButton(text = body)
            }
            Spacer(Modifier.height(2.dp))
            SelectionContainer {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun GoalBanner(goal: String, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "GOAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(54.dp).padding(top = 2.dp),
            )
            Text(
                text = goal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear goal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** The model's question with its options and a free-text reply. */
@Composable
private fun QuestionCard(question: PendingQuestion, onAnswer: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = question.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            question.options.forEach { option ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { onAnswer(option) },
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f),
                ) {
                    BasicTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (custom.isEmpty()) {
                                    Text(
                                        text = "Write your own reply…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }

                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { onAnswer(custom) },
                    enabled = custom.isNotBlank(),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send reply",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: PendingApproval, onDecision: (Boolean) -> Unit) {
    // The pending call is already rendered above; this only adds the decision.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Allow this tool?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = approval.toolName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))
            OutlinedIconButton(onClick = { onDecision(false) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Deny",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { onDecision(true) }) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Allow",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Ask Xed AI…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = { if (running) onStop() else onSend() },
                enabled = running || value.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                if (running) {
                    Box(
                        Modifier.size(14.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimary,
                                RoundedCornerShape(3.dp),
                            )
                    )
                } else {
                    Icon(
                        imageVector = ArrowUpward,
                        contentDescription = "Send",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

private fun PermissionMode.label(): String =
    when (this) {
        PermissionMode.ASK -> "ask before changes"
        PermissionMode.ACCEPT_EDITS -> "auto-approve edits"
        PermissionMode.PLAN -> "plan only"
        PermissionMode.YOLO -> "autonomous"
    }

private data class Suggestion(val title: String, val subtitle: String, val prompt: String)

/** How long the copy button shows its tick. */
private const val COPIED_FEEDBACK_MILLIS = 1_500L

private val SUGGESTIONS =
    listOf(
        Suggestion(
            "Explain this project",
            "Summarise the structure and the key files",
            "Give me a quick overview of this project: what it does and how it is organised.",
        ),
        Suggestion(
            "Find a bug",
            "Search the code for likely problems",
            "Look through the project for a likely bug or rough edge and explain what you find.",
        ),
        Suggestion(
            "Add a feature",
            "Describe it and let me edit the files",
            "I want to add a small feature. Ask me what it should do, then implement it.",
        ),
    )
