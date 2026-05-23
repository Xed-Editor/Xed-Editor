package com.rk.ai.core

sealed class AiError(
    val code: String,
    override val message: String,
    val userMessage: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    class Validation(field: String, detail: String = "") :
        AiError("VALIDATION", "Validation failed for '$field'${if (detail.isNotBlank()) ": $detail" else ""}",
            "Invalid input: $field${if (detail.isNotBlank()) " — $detail" else ""}")

    class Timeout(timeoutMs: Long, operation: String = "request") :
        AiError("TIMEOUT", "$operation timed out after ${timeoutMs}ms",
            "Request timed out. Please try again.",
            cause = null)

    class ProviderUnavailable(providerId: String, detail: String = "") :
        AiError("PROVIDER_UNAVAILABLE", "Provider $providerId unavailable${if (detail.isNotBlank()) ": $detail" else ""}",
            "AI provider ($providerId) is currently unavailable. Check your connection and try again.",
            cause = null)

    class Authentication(providerId: String, detail: String = "") :
        AiError("AUTH_ERROR", "Authentication failed for $providerId${if (detail.isNotBlank()) ": $detail" else ""}",
            "Authentication failed. Please check your API key for $providerId.",
            cause = null)

    class RateLimit(providerId: String, val retryAfterMs: Long = 0) :
        AiError("RATE_LIMIT", "Rate limited by $providerId",
            "Too many requests. ${if (retryAfterMs > 0) "Retry in ${retryAfterMs / 1000}s." else "Please slow down and try again."}",
            cause = null)

    class Network(override val cause: Throwable) :
        AiError("NETWORK", "Network error: ${cause.message ?: "unknown"}",
            "Network error. Check your internet connection.",
            cause = cause)

    class Serialization(override val cause: Throwable) :
        AiError("SERIALIZATION", "Serialization error: ${cause.message ?: "unknown"}",
            "Failed to process server response.",
            cause = cause)

    class Cancelled(operation: String = "request") :
        AiError("CANCELLED", "Operation cancelled: $operation",
            "Request was cancelled.")

    class ModelNotFound(modelId: String, providerId: String) :
        AiError("MODEL_NOT_FOUND", "Model '$modelId' not found for provider '$providerId'",
            "Model '$modelId' is not available. Check your model configuration.")

    class WorkspaceViolation(path: String) :
        AiError("WORKSPACE_VIOLATION", "Path outside workspace: $path",
            "Access denied: file is outside the workspace.")

    class InvalidState(operation: String, reason: String) :
        AiError("INVALID_STATE", "Invalid state for $operation: $reason",
            "The operation cannot be completed in the current state.")

    class Internal(override val cause: Throwable) :
        AiError("INTERNAL", "Internal error: ${cause.message ?: "unknown cause"}",
            "An unexpected error occurred. Please try again.",
            cause = cause)

    companion object {
        fun fromThrowable(t: Throwable, operation: String = "request"): AiError {
            return when {
                t is AiError -> t
                t is kotlinx.coroutines.TimeoutCancellationException -> Timeout(0, operation)
                t is kotlinx.coroutines.CancellationException -> Cancelled(operation)
                t is java.io.IOException -> Network(t)
                t is kotlinx.serialization.SerializationException -> Serialization(t)
                else -> Internal(t)
            }
        }
    }
}
