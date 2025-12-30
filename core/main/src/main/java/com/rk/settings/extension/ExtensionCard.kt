package com.rk.settings.extension

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.extension.Extension
import com.rk.icons.Download
import com.rk.icons.XedIcons
import com.rk.resources.strings
import kotlinx.coroutines.launch

enum class InstallState {
    Idle,
    Installing,
    Installed,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtensionCard(
    plugin: Extension,
    modifier: Modifier = Modifier,
    installState: InstallState = InstallState.Idle,
    onInstallClick: suspend (Extension) -> Unit,
    onUninstallClick: suspend (Extension) -> Unit,
    onLongPress: suspend (Extension) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onLongClick = { scope.launch { onLongPress(plugin) } }, onClick = {}),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = "by ${plugin.authors.joinToString()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (installState) {
                    InstallState.Idle -> {
                        Button(
                            onClick = { scope.launch { onInstallClick(plugin) } },
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        ) {
                            Icon(XedIcons.Download, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(strings.install))
                        }
                    }

                    InstallState.Installing -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(10.dp),
                            colors =
                                ButtonDefaults.buttonColors(disabledContentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(strings.installing))
                        }
                    }

                    InstallState.Installed -> {
                        Button(
                            onClick = { scope.launch { onUninstallClick(plugin) } },
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(strings.uninstall))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
