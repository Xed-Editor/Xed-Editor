package com.rk.extension

import com.rk.extension.scanner.Finding

sealed interface InstallResult {
    data class Success(val extension: LocalExtension, val performedUpdate: Boolean) : InstallResult

    data class ValidationFailed(val error: Throwable?) : InstallResult

    data class Error(val error: ExtensionError) : InstallResult

    data class ScanRejected(val findings: List<Finding>) : InstallResult

    data object Cancelled : InstallResult
}

enum class ExtensionError {
    OUTDATED_CLIENT
}
