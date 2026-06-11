package com.rk.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    if (aiSession != null && isAiRunning) {
        SheetTerminal(session = aiSession, modifier = Modifier.fillMaxSize(), showKeys = false)
    } else {
        AgentEmptyState(
            isRunning = isAiRunning,
            agentName = agentName,
            onStart = onStart,
        )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            showKeys = false,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
