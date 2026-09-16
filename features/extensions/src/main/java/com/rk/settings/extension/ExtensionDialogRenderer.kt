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
import com.rk.extension.ui.ExtensionScanReportDialog
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.launch

/**
 * Renders extension related dialogs such as missing dependencies, recommendations or the extension warning.
 *
 * @see ExtensionDialogManager
 */
@Composable
fun ExtensionDialogRenderer(dialogManager: ExtensionDialogManager) {
    val dialog = dialogManager.activeDialog ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity

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
                    TextButton(onClick = { dialogManager.onDismiss() }) {
                        Text(stringResource(strings.cancel))
                    }
                },
            )
        }

        is ExtensionDialog.Dependencies -> {
            DependenciesDialog(
                extensionIds = dialog.missing,
                scope = scope,
                activity = activity,
                onDismiss = { dialogManager.onDismiss() },
                onScanFindings = dialogManager.asScanApproval(),
            ) {
                dialogManager.onApproved()
            }
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
                scope = scope,
                activity = activity,
                onInstallClick = { dependency ->
                    val scanApproval = dialogManager.asScanApproval()
                    val missing = getMissingDependencies(dependency)
                    if (missing.isNotEmpty()) {
                        dialogManager.showDependencies(dependency, missing) {
                            runExtensionInstallAction(dependency, {}, context, activity, scanApproval)
                        }
                    } else {
                        scope.launch {
                            runExtensionInstallAction(
                                extension = dependency,
                                updateInstallState = {},
                                context = context,
                                activity = activity,
                                onScanFindings = scanApproval,
                            )
                        }
                    }
                },
                onUpdateClick = { dependency ->
                    if (dependency !is UpdatableExtension) return@RecommendationsDialog
                    val scanApproval = dialogManager.asScanApproval()
                    val missing = getMissingDependencies(dependency)
                    if (missing.isNotEmpty()) {
                        dialogManager.showDependencies(dependency, missing) {
                            runExtensionUpdateAction(dependency, {}, context, activity, scanApproval)
                        }
                    } else {
                        scope.launch {
                            runExtensionUpdateAction(
                                extension = dependency,
                                updateInstallState = {},
                                context = context,
                                activity = activity,
                                onScanFindings = scanApproval,
                            )
                        }
                    }
                },
                onDismiss = { dialogManager.onDismiss() },
            )
        }
    }
}
