package com.rk.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.ui.VibeCodingPanel
import com.rk.git.GitViewModel
import com.rk.tabs.editor.SheetTerminal
import com.rk.terminal.TerminalViewModel
import com.termux.terminal.TerminalSession

@Composable
fun AiPanel(
    aiSession: TerminalSession?,
    isAiRunning: Boolean,
    agentName: String,
    onStart: () -> Unit,
    cwd: String = "",
    transcript: String = "",
    onClearTranscript: () -> Unit = {},
    onToggleTranscript: () -> Unit = {},
) {
    if (aiSession != null && isAiRunning) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiSessionInfoBar(
                agentName = agentName,
                isRunning = isAiRunning,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                thickness = 0.5.dp,
            )
            SheetTerminal(session = aiSession, modifier = Modifier.weight(1f).fillMaxWidth(), showKeys = false)
        }
    } else {
        AiSessionOverview(
            isRunning = isAiRunning,
            agentName = agentName,
            onStart = onStart,
            cwd = cwd,
            transcript = transcript,
            onClearTranscript = onClearTranscript,
            onToggleTranscript = onToggleTranscript,
        )
    }
}

@Composable
private fun AiSessionInfoBar(
    agentName: String,
    isRunning: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isRunning) Color(0xFF4CAF50) else Color(0xFFEF5350)))
            Spacer(Modifier.width(6.dp))
            Text(
                agentName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Text(
                    if (isRunning) "Running" else "Idle",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun VibeCodingPanelContent(
    engine: VibeCodingEngine?,
) {
    if (engine != null) {
        VibeCodingPanel(engine = engine)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "VibeCoding unavailable",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun TerminalPanelContent(
    terminalViewModel: TerminalViewModel,
    initialCwd: String?,
    onCwdConsumed: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        com.rk.terminal.TerminalPanel(
            terminalViewModel = terminalViewModel,
            showKeys = true,
            initialCwd = initialCwd,
        )
        LaunchedEffect(initialCwd) {
            if (initialCwd != null) {
                onCwdConsumed()
            }
        }
    }
}

@Composable
fun GitPanelContent(
    gitViewModel: GitViewModel?,
    onRefresh: () -> Unit,
) {
    if (gitViewModel != null) {
        GitPanel(
            gitViewModel = gitViewModel,
            onRefresh = onRefresh,
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Git not available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
