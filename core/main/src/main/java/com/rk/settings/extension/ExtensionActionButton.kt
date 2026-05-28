package com.rk.settings.extension

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.extension.Extension
import com.rk.extension.UpdatableExtension
import com.rk.icons.Download
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class InstallState {
    Idle,
    Installing,
    Installed,
    Updatable,
    Updating,
}

@Composable
fun ExtensionActionButton(
    extension: Extension,
    installState: InstallState,
    scope: CoroutineScope,
    onInstallClick: suspend (Extension) -> Unit,
    onUninstallClick: suspend (Extension) -> Unit,
    onUpdateClick: suspend (UpdatableExtension) -> Unit,
) {
    when (installState) {
        InstallState.Idle -> {
            Button(
                onClick = { scope.launch { onInstallClick(extension) } },
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(disabledContentColor = MaterialTheme.colorScheme.onSurface),
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
                onClick = { scope.launch { onUninstallClick(extension) } },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(strings.delete), Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(strings.uninstall))
            }
        }

        InstallState.Updatable -> {
            val updatableExtension = extension as UpdatableExtension
            Button(
                onClick = { scope.launch { onUpdateClick(updatableExtension) } },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
            ) {
                Icon(
                    painterResource(drawables.update),
                    contentDescription = stringResource(strings.update),
                    Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(strings.update))
            }
        }

        InstallState.Updating -> {
            Button(
                onClick = {},
                enabled = false,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(disabledContentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(strings.updating))
            }
        }
    }
}
