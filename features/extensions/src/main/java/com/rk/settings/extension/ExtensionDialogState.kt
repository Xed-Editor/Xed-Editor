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

/**
 * Manages the dialogs shown while an extension is installed, updated or reviewed.
 *
 * The blocking dialogs (warning, missing dependencies, security scan report) are awaited by the install coroutine
 * through [awaitWarning], [awaitDependencies] and [awaitScanApproval], so the store can render them from any screen
 * with [ExtensionDialogRenderer].
 *
 * An instance is owned by the extensions store section, which hosts the install actions.
 */
class ExtensionDialogManager {
    var activeDialog by mutableStateOf<ExtensionDialog?>(null)
        private set

    // Written from the install coroutine (IO) and invoked from the UI thread.
    @Volatile private var pendingDecision: ((Boolean) -> Unit)? = null

    suspend fun awaitWarning(): Boolean = awaitDecision(ExtensionDialog.Warning)

    suspend fun awaitDependencies(extension: Extension, missing: List<ExtensionId>): Boolean =
        awaitDecision(ExtensionDialog.Dependencies(extension, missing))

    suspend fun awaitScanApproval(extensionName: String, findings: List<Finding>): Boolean =
        awaitDecision(ExtensionDialog.ScanReport(extensionName, findings))

    fun showRecommendations(extension: Extension, missing: List<ExtensionId>) {
        activeDialog = ExtensionDialog.Recommendations(extension, missing)
    }

    fun onApproved() {
        val decision = pendingDecision
        pendingDecision = null
        activeDialog = null
        decision?.invoke(true)
    }

    fun onDismiss() {
        val decision = pendingDecision
        pendingDecision = null
        activeDialog = null
        decision?.invoke(false)
    }

    private suspend fun awaitDecision(dialog: ExtensionDialog): Boolean = suspendCancellableCoroutine { continuation ->
        val previous = pendingDecision
        pendingDecision = { approved ->
            if (continuation.isActive) {
                continuation.resume(approved)
            }
        }
        activeDialog = dialog
        previous?.invoke(false)

        continuation.invokeOnCancellation {
            pendingDecision = null
            if (activeDialog == dialog) {
                activeDialog = null
            }
        }
    }
}

fun ExtensionDialogManager.asScanApproval(): ScanApproval = { name, findings -> awaitScanApproval(name, findings) }
