package com.rk.settings.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
fun SmallExtensionActionButton(
    extension: Extension,
    installState: InstallState,
    scope: CoroutineScope,
    onInstallClick: suspend (Extension) -> Unit,
    onUninstallClick: suspend (Extension) -> Unit,
    onUpdateClick: suspend (UpdatableExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (installState) {
        InstallState.Idle -> {
            IconButton(modifier = modifier, onClick = { scope.launch { onInstallClick(extension) } }) {
                Icon(XedIcons.Download, contentDescription = null)
            }
        }

        InstallState.Installing -> {
            IconButton(modifier = modifier, onClick = {}, enabled = false) {
                Icon(XedIcons.Download, contentDescription = null)
            }
        }

        InstallState.Installed -> {
            IconButton(
                modifier = modifier,
                onClick = { scope.launch { onUninstallClick(extension) } },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(strings.delete))
            }
        }

        InstallState.Updatable -> {
            val updatableExtension = extension as UpdatableExtension
            IconButton(
                modifier = modifier,
                onClick = { scope.launch { onUpdateClick(updatableExtension) } },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(painterResource(drawables.update), contentDescription = stringResource(strings.update))
            }
        }

        InstallState.Updating -> {
            IconButton(modifier = modifier, onClick = {}, enabled = false) {
                Icon(painterResource(drawables.update), contentDescription = stringResource(strings.update))
            }
        }
    }
}

@Composable
fun ExtensionActionButtons(
    extension: Extension,
    installState: InstallState,
    scope: CoroutineScope,
    onInstallClick: suspend (Extension) -> Unit,
    onUninstallClick: suspend (Extension) -> Unit,
    onUpdateClick: suspend (UpdatableExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (installState) {
        InstallState.Idle -> {
            InstallButton(scope, onInstallClick, extension, modifier)
        }

        InstallState.Installing -> {
            InstallingButton(modifier)
        }

        InstallState.Installed -> {
            UninstallButton(scope, onUninstallClick, extension, modifier)
        }

        InstallState.Updatable -> {
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateButton(extension, scope, onUpdateClick, Modifier.weight(1f))
                UninstallButton(scope, onUninstallClick, extension, Modifier.weight(1f))
            }
        }

        InstallState.Updating -> {
            UpdatingButton(modifier)
        }
    }
}

@Composable
private fun InstallButton(
    scope: CoroutineScope,
    onInstallClick: suspend (Extension) -> Unit,
    extension: Extension,
    modifier: Modifier = Modifier,
) {
    Button(modifier = modifier, onClick = { scope.launch { onInstallClick(extension) } }) {
        Icon(XedIcons.Download, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(strings.install))
    }
}

@Composable
private fun InstallingButton(modifier: Modifier = Modifier) {
    Button(modifier = modifier, onClick = {}, enabled = false) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(strings.installing))
    }
}

@Composable
private fun UninstallButton(
    scope: CoroutineScope,
    onUninstallClick: suspend (Extension) -> Unit,
    extension: Extension,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        onClick = { scope.launch { onUninstallClick(extension) } },
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

@Composable
private fun UpdateButton(
    extension: Extension,
    scope: CoroutineScope,
    onUpdateClick: suspend (UpdatableExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updatableExtension = extension as UpdatableExtension
    Button(
        modifier = modifier,
        onClick = { scope.launch { onUpdateClick(updatableExtension) } },
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

@Composable
private fun UpdatingButton(modifier: Modifier = Modifier) {
    Button(modifier = modifier, onClick = {}, enabled = false) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(strings.updating))
    }
}
