@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.ai.providers.Model
import com.rk.ai.providers.ProviderSetting
import kotlin.uuid.ExperimentalUuidApi

@Composable
internal fun ModelSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    selectedModelId: kotlin.uuid.Uuid,
    allChatModels: List<Pair<ProviderSetting, Model>>,
    onSelectModel: (kotlin.uuid.Uuid) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Model",
                icon = { Icon(Icons.Outlined.ModelTraining, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentModel = settings.providers.flatMap { it.models }
                    .firstOrNull { it.id == settings.chatModelId }
                Text(
                    text = currentModel?.displayName?.ifEmpty { currentModel?.modelId } ?: "No model",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                if (allChatModels.isEmpty()) {
                    Text(
                        text = "No models available. Add a provider below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        allChatModels.forEach { (provider, model) ->
                            val isSelected = model.id == selectedModelId
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectModel(model.id) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f) else colorScheme.surface,
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
                                        Icon(Icons.Outlined.Check, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}
