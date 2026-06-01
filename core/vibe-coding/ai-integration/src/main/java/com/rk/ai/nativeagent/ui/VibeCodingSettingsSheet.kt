package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.providers.Model
import com.rk.ai.providers.ModelType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeCodingSettingsSheet(
    engine: VibeCodingEngine,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by engine.settingsStore.settingsFlow.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var selectedModelId by remember { mutableStateOf(settings.chatModelId) }

    val allModels: List<Pair<ProviderSetting, Model>> = remember(settings) {
        settings.providers
            .filter { it.enabled }
            .flatMap { provider ->
                provider.models
                    .filter { it.type == ModelType.CHAT }
                    .map { model -> provider to model }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "VibeCoding Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Model",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            if (allModels.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = "No models available. Add a provider in AI settings.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(allModels) { (provider, model) ->
                        val isSelected = model.id == selectedModelId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedModelId = model.id
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else colorScheme.surface,
                            tonalElevation = if (isSelected) 1.dp else 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName.ifEmpty { model.modelId },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val id = selectedModelId
                    if (id != null) {
                        scope.launch {
                            engine.settingsStore.update { it.copy(chatModelId = id) }
                        }
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedModelId != null,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Apply")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
