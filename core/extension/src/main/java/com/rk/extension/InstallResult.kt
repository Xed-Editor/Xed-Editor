package com.rk.extension

sealed interface InstallResult {
    data class Success(val extension: LocalExtension) : InstallResult
    data class ValidationFailed(val error: Throwable?) : InstallResult
    data class AlreadyInstalled(val extensionId: String) : InstallResult
    data class Error(val message: String) : InstallResult
}
