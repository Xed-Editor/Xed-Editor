package com.rk.extension

sealed interface InstallResult {
    data class Success(val extension: LocalExtension) : InstallResult

    data class ValidationFailed(val error: Throwable?) : InstallResult

    class AlreadyInstalled() : InstallResult

    data class Error(val error: ExtensionError) : InstallResult
}

enum class ExtensionError {
    OUTDATED_CLIENT,
    OUTDATED_EXTENSION,
}
