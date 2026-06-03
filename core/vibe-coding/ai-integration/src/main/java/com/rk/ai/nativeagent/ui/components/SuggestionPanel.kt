package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import kotlinx.serialization.json.*
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.Tool
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SuggestionPanel(
    engine: VibeCodingEngine,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = engine.suggestionsFlow.collectAsState()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Suggestions", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }
            Divider()
            if (suggestions.value.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No suggestions available", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
                    items(suggestions.value) { sug ->
                        SuggestionItem(suggestion = sug, engine = engine)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(suggestion: JsonObject, engine: VibeCodingEngine) {
    val text = suggestion["text"]?.jsonPrimitive?.content ?: "<no text>"
    val confidence = suggestion["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f
    val confidenceColor = when {
        confidence >= 0.8f -> Color(0xFF4CAF50)
        confidence >= 0.5f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Confidence: ${"%.2f".format(confidence)}", style = MaterialTheme.typography.labelSmall, color = confidenceColor)
            }
            Row {
                TextButton(onClick = {
                    val applyJson = buildJsonObject {
                        put("suggestion", suggestion.toString())
                        put("dryRun", false)
                    }
                    engine.sendMessage("applySuggestion", listOf(UIMessagePart.Text(applyJson.toString())))
                }) { Text("Apply") }
                TextButton(onClick = {
                    val clipJson = buildJsonObject { put("text", text) }
                    engine.sendMessage("writeToClipboard", listOf(UIMessagePart.Text(clipJson.toString())))
                }) { Text("Copy") }
                TextButton(onClick = {
                    val id = suggestion["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString()
                    val feedbackJson = buildJsonObject {
                        put("suggestionId", id)
                        put("accepted", false)
                    }
                    engine.sendMessage("recordSuggestionFeedback", listOf(UIMessagePart.Text(feedbackJson.toString())))
                }) { Text("Dismiss") }
            }
        }
    }
}
