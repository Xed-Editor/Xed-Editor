package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import kotlinx.coroutines.launch

@Composable
internal fun WebSearchSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    engine: VibeCodingEngine,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Web Search",
                icon = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enable web search for real-time info", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.enableWebSearch,
                    onCheckedChange = {
                        scope.launch {
                            engine.settingsStore.update { s -> s.copy(enableWebSearch = it) }
                        }
                    },
                )
            }
        }
    }
}
