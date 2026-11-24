package com.rk.settings.extension

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.extension.UpdatableExtension
import com.rk.extension.store.requestExtensionInstall
import com.rk.extension.store.requestExtensionUninstall
import com.rk.extension.store.requestExtensionUpdate
import com.rk.extension.ui.ExtensionScanReportDialog
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.store.actions.rememberPackageActionContext

/**
 * Renders the dialogs managed by [ExtensionDialogManager]: the extension warning, missing dependencies, recommendations
 * and the pre-install security scan report.
 */
@Composable
fun ExtensionDialogRenderer(dialogManager: ExtensionDialogManager) {
    val dialog = dialogManager.activeDialog ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val action = rememberPackageActionContext(context, activity, scope)

    when (dialog) {
        is ExtensionDialog.Warning -> {
            AlertDialog(
                onDismissRequest = { dialogManager.onDismiss() },
                title = { Text(stringResource(strings.attention)) },
                text = { Text(stringResource(strings.extension_warning_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Settings.warn_extensions = false
                            dialogManager.onApproved()
                        }
                    ) {
                        Text(stringResource(strings.continue_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogManager.onDismiss() }) { Text(stringResource(strings.cancel)) }
                },
            )
        }

        is ExtensionDialog.Dependencies -> {
            DependenciesDialog(
                extensionIds = dialog.missing,
                onContinue = { dialogManager.onApproved() },
                onDismiss = { dialogManager.onDismiss() },
            )
        }

        is ExtensionDialog.ScanReport -> {
            ExtensionScanReportDialog(
                extensionName = dialog.extensionName,
                findings = dialog.findings,
                onInstall = { dialogManager.onApproved() },
                onCancel = { dialogManager.onDismiss() },
            )
        }

        is ExtensionDialog.Recommendations -> {
            RecommendationsDialog(
                extensionIds = dialog.missing,
                action = action,
                onInstallClick = { dependency -> requestExtensionInstall(dependency, action, dialogManager) },
                onUpdateClick = { dependency ->
                    (dependency as? UpdatableExtension)?.let { requestExtensionUpdate(it, action, dialogManager) }
                },
                onUninstallClick = { dependency -> requestExtensionUninstall(dependency, action) },
                onDismiss = { dialogManager.onDismiss() },
            )
        }
    }
}
