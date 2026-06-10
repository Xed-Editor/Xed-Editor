package com.rk.settings.terminal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.icons.LucideCircleQuestionMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

enum class CheckStatus {
    PENDING,
    LOADING,
    SUCCESS,
    FAILED
}

data class Check(
    val label: String,
    val status: CheckStatus = CheckStatus.PENDING,
    val logs: List<String> = emptyList(),
    val isExpanded: Boolean = false,
    val run: suspend (printLog: (String) -> Unit) -> Boolean
)

@Composable
fun TerminalCheckScreen(modifier: Modifier = Modifier) {
    val checks = terminalChecks()

    LaunchedEffect(Unit) {
        for (i in checks.indices) {
            val check = checks[i]
            // Update to LOADING and clear logs
            checks[i] = check.copy(status = CheckStatus.LOADING, logs = emptyList())

            val success = try {
                val start = System.currentTimeMillis()

                val result = check.run { log ->
                    // Append log to the current check's logs
                    checks[i] = checks[i].copy(logs = checks[i].logs + log)
                }

                val end = System.currentTimeMillis()

                if ((end-start) < 1000){
                    delay(1000)
                }

                result
            } catch (e: Exception) {
                checks[i] = checks[i].copy(logs = checks[i].logs + "Crash: ${e.localizedMessage}")
                false
            }

            checks[i] = checks[i].copy(
                status = if (success) CheckStatus.SUCCESS else CheckStatus.FAILED,
                isExpanded = !success
            )
        }
    }

    PreferenceLayout(label = "Terminal Check", backArrowVisible = true) {

            checks.forEachIndexed { index, check ->
                PreferenceGroup() {
                    PreferenceTemplate(
                        modifier = Modifier.clickable {
                            checks[index] = check.copy(isExpanded = !check.isExpanded)
                        },
                        title = { Text(text = check.label, style = MaterialTheme.typography.bodyLarge) },
                        startWidget = { StatusIcon(status = check.status) },
                        endWidget = {
                            val rotation by animateFloatAsState(
                                targetValue = if (check.isExpanded) 90f else 0f,
                                label = "ChevronRotation",
                            )

                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = if (check.isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.rotate(rotation).size(24.dp),
                            )
                        },
                    )

                    if (check.isExpanded) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                if (check.logs.isEmpty()) {
                                    Text(
                                        text = "No logs available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    check.logs.forEach { log ->
                                        Text(
                                            text = if (log.startsWith(">")) log else "> $log",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            ),
                                            color = if (log.contains("Error", ignoreCase = true) || log.contains("Fail", ignoreCase = true) || log.contains("Crash", ignoreCase = true))
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

    }
}

@Composable
private fun StatusIcon(status: CheckStatus) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (status) {
            CheckStatus.PENDING -> {
                Icon(
                    imageVector = LucideCircleQuestionMark,
                    contentDescription = "Pending",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }

            CheckStatus.LOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            CheckStatus.SUCCESS -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp),
                )
            }

            CheckStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
