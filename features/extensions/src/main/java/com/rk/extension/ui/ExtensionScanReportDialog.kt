package com.rk.extension.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.extension.scanner.Finding
import com.rk.extension.scanner.FindingCategory
import com.rk.extension.scanner.FindingSeverity
import com.rk.resources.strings


@Composable
fun ExtensionScanReportDialog(
    extensionName: String,
    findings: List<Finding>,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val blocking = findings.filter { it.isBlocking }
    val suspicious = findings.filterNot { it.isBlocking }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(strings.security_scan)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(strings.security_scan_desc, extensionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (findings.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(strings.security_scan_clean), style = MaterialTheme.typography.bodyMedium)
                }

                if (blocking.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(
                        text = stringResource(strings.security_scan_restricted),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(strings.security_scan_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    blocking.forEach { FindingRow(it) }
                }

                if (suspicious.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    suspicious.forEach { FindingRow(it) }
                }
            }
        },
        confirmButton = {
            if (blocking.isEmpty()) {
                TextButton(onClick = onInstall) {
                    Text(stringResource(strings.install))
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text(stringResource(strings.close))
                }
            }
        },
        dismissButton = {
            if (blocking.isEmpty()) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(strings.cancel))
                }
            }
        },
    )
}

@Composable
private fun SectionHeader(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
private fun FindingRow(finding: Finding) {
    val color =
        when (finding.severity) {
            FindingSeverity.CRITICAL -> MaterialTheme.colorScheme.error
            FindingSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
            FindingSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val location =
        buildString {
            append(finding.className.trimStart('L').trimEnd(';').replace('/', '.'))
            finding.methodName?.let { append('#').append(it) }
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(color))

        Column(Modifier.weight(1f)) {
            Text(text = finding.message, style = MaterialTheme.typography.bodyMedium, color = color)
            Text(
                text = "$location • ${finding.category.readableName()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun FindingCategory.readableName(): String = name.lowercase().replace('_', ' ')
