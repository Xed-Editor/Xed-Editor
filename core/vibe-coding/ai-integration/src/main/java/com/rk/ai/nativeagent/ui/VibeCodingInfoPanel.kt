package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.engine.VibeCodingState
import com.rk.ai.nativeagent.ui.panels.*

/** Tabs shown in the info panel area. */
internal enum class InfoTab { PLAN, TOOLS, CONTEXT, CHANGES }

@Composable
internal fun VibeCodingInfoPanel(
    selectedInfoTab: InfoTab,
    state: VibeCodingState,
    engine: VibeCodingEngine,
    colorScheme: ColorScheme,
    onSelectTab: (InfoTab) -> Unit,
) {
    val visibleTabs = remember { InfoTab.entries.toList() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceContainerLowest,
    ) {
        Column {
            // Tab chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(visibleTabs) { tab ->
                    val label = tab.name.lowercase().replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = tab == selectedInfoTab,
                        onClick = { onSelectTab(tab) },
                        label = {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.height(24.dp),
                    )
                }
            }

            // Tab content
            when (selectedInfoTab) {
                InfoTab.PLAN -> ExecutionGraphPanel(
                    taskTree = state.taskTree,
                    modifier = Modifier.weight(1f),
                )
                InfoTab.TOOLS -> ToolActivityPanel(
                    toolExecutions = state.toolExecutions,
                    modifier = Modifier.weight(1f),
                )
                InfoTab.CONTEXT -> {
                    val contextInfo = ContextInfo(
                        currentGoal = engine.contextMemoryManager.conversation.getCurrentGoal(),
                        modifiedFiles = state.modifiedFiles,
                        projectIndexed = state.projectIndexed,
                        toolCalls = state.toolExecutions.size,
                        workspacePath = state.workspacePath,
                    )
                    ContextPanel(info = contextInfo, modifier = Modifier.weight(1f))
                }
                InfoTab.CHANGES -> FileChangePanel(
                    modifiedFiles = state.modifiedFiles,
                    currentPhase = state.currentPhase,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
