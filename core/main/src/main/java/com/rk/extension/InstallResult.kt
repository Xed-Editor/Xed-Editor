package com.rk.extension

sealed interface InstallResult {
    data class Success(val extension: LocalExtension, val performedUpdate: Boolean) : InstallResult

    data class ValidationFailed(val error: Throwable?) : InstallResult

    data class Error(val error: ExtensionError) : InstallResult
}

enum class ExtensionError {
    OUTDATED_CLIENT,
    OUTDATED_EXTENSION,
}
