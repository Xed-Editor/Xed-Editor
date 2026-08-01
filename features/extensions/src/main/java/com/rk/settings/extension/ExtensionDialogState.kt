package com.rk.settings.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.extension.Extension
import com.rk.extension.model.ExtensionId

sealed class ExtensionDialog {
    object Warning : ExtensionDialog()

    data class Dependencies(val extension: Extension, val missing: List<ExtensionId>) : ExtensionDialog()

    data class Recommendations(val extension: Extension, val missing: List<ExtensionId>) : ExtensionDialog()
}

/**
 * Manages the currently opened dialogs related to extensions.
 *
 * An ExtensionDialogManager instance should be constructed per UI screen.
 *
 * @see ExtensionDialogRenderer
 */
class ExtensionDialogManager {
    var activeDialog by mutableStateOf<ExtensionDialog?>(null)
        private set

    private var pendingAction: (() -> Unit)? = null

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

    fun onApproved() {
        activeDialog = null
        pendingAction?.invoke()
        pendingAction = null
    }

    fun onDismiss() {
        activeDialog = null
        pendingAction = null
    }
}
