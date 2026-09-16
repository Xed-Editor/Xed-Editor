package com.rk.settings.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.extension.Extension
import com.rk.extension.model.ExtensionId
import com.rk.extension.scanner.Finding
import com.rk.extension.scanner.ScanApproval
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class ExtensionDialog {
    object Warning : ExtensionDialog()

    data class Dependencies(val extension: Extension, val missing: List<ExtensionId>) : ExtensionDialog()

    data class Recommendations(val extension: Extension, val missing: List<ExtensionId>) : ExtensionDialog()

    data class ScanReport(val extensionName: String, val findings: List<Finding>) : ExtensionDialog()
}


class ExtensionDialogManager {
    var activeDialog by mutableStateOf<ExtensionDialog?>(null)
        private set

    private var pendingAction: (() -> Unit)? = null

    // Written from the install coroutine (IO) and invoked from the UI thread.
    @Volatile private var pendingDecision: ((Boolean) -> Unit)? = null

    fun showWarning(onApproved: () -> Unit) {
        pendingAction = onApproved
        activeDialog = ExtensionDialog.Warning
    }

    fun showDependencies(extension: Extension, missing: List<ExtensionId>, onApproved: () -> Unit) {
        pendingAction = onApproved
        activeDialog = ExtensionDialog.Dependencies(extension, missing)
    }

    fun showRecommendations(extension: Extension, missing: List<ExtensionId>) {
        activeDialog = ExtensionDialog.Recommendations(extension, missing)
    }

    suspend fun awaitScanApproval(extensionName: String, findings: List<Finding>): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingDecision = { approved ->
                if (continuation.isActive) {
                    continuation.resume(approved)
                }
            }
            activeDialog = ExtensionDialog.ScanReport(extensionName, findings)

            continuation.invokeOnCancellation { clearScanReport() }
        }

    fun onApproved() {
        activeDialog = null
        pendingDecision?.invoke(true)
        pendingDecision = null
        pendingAction?.invoke()
        pendingAction = null
    }

    fun onDismiss() {
        activeDialog = null
        pendingDecision?.invoke(false)
        pendingDecision = null
        pendingAction = null
    }

    private fun clearScanReport() {
        pendingDecision = null
        if (activeDialog is ExtensionDialog.ScanReport) {
            activeDialog = null
        }
    }
}

fun ExtensionDialogManager.asScanApproval(): ScanApproval = { name, findings -> awaitScanApproval(name, findings) }
