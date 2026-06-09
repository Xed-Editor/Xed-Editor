package com.rk.ai.agent

sealed class VibeCodingError(
    open val message: String,
    open val cause: Throwable? = null,
) {
    override fun toString(): String = buildString {
        append("[${this@VibeCodingError::class.simpleName}]")
        append(" $message")
        if (cause != null) append(" (cause: ${cause?.message})")
    }

    // ── Tool errors ──
    sealed class ToolError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class NotFound(val toolName: String) : ToolError("Tool '$toolName' not found in registry")
        data class ExecutionFailed(val toolName: String, override val cause: Throwable) : ToolError("Tool '$toolName' execution failed", cause)
        data class InvalidArgs(val toolName: String, val validationErrors: List<String>) : ToolError("Invalid arguments for '$toolName': ${validationErrors.joinToString("; ")}")
        data class PermissionDenied(val toolName: String, val reason: String) : ToolError("Permission denied for '$toolName': $reason")
        data class ValidationError(val toolName: String, val schemaPath: String, val actual: String) : ToolError("Validation failed for '$toolName' at $schemaPath: expected $actual")
    }

    // ── Config errors ──
    sealed class ConfigError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class ParseError(val configPath: String, override val cause: Throwable) : ConfigError("Failed to parse config at $configPath", cause)
        data class NotFound(val configPath: String) : ConfigError("Config not found at $configPath")
        data class ValidationError(val configPath: String, val field: String, val reason: String) : ConfigError("Config at $configPath has invalid field '$field': $reason")
    }

    // ── Agent errors ──
    sealed class AgentError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class ExecutionFailed(val agentName: String, val taskId: String, override val cause: Throwable) : AgentError("Agent '$agentName' execution failed for task $taskId", cause)
        data class NotFound(val agentName: String) : AgentError("Agent '$agentName' not found")
        data class NotAvailable(val agentName: String, val reason: String) : AgentError("Agent '$agentName' not available: $reason")
        data class MaxStepsExceeded(val agentName: String, val taskId: String, val maxSteps: Int) : AgentError("Agent '$agentName' exceeded max steps ($maxSteps) for task $taskId")
    }

    // ── Generation errors ──
    sealed class GenerationError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class ModelNotFound(val modelId: String) : GenerationError("Model '$modelId' not found in any provider")
        data class ProviderFailed(val modelId: String, val providerName: String, override val cause: Throwable) : GenerationError("Provider '$providerName' failed for model '$modelId'", cause)
        data class CompactionFailed(override val cause: Throwable) : GenerationError("Context compaction failed", cause)
        data class MaxCompactionsExceeded(val count: Int) : GenerationError("Max compactions exceeded ($count)")
        data class DoomLoopDetected(val toolName: String) : GenerationError("Doom loop detected: repeated calls to '$toolName'")
        data class ContextOverflow(val contextUsed: Int, val contextLimit: Int) : GenerationError("Context overflow: $contextUsed/$contextLimit tokens used")
    }

    // ── Persistence errors ──
    sealed class PersistenceError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class DatabaseError(override val cause: Throwable) : PersistenceError("Database operation failed", cause)
        data class SerializationError(override val cause: Throwable) : PersistenceError("Serialization failed", cause)
    }

    // ── Security errors ──
    sealed class SecurityError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class Blocked(val toolName: String, val pattern: String, val severity: String) : SecurityError("Blocked by security hook: $pattern ($severity) in tool '$toolName'")
        data class Warning(val toolName: String, val pattern: String, val severity: String) : SecurityError("Security warning: $pattern ($severity) in tool '$toolName'")
    }

    // ── Plugin errors ──
    sealed class PluginError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class LoadFailed(val pluginId: String, override val cause: Throwable) : PluginError("Plugin '$pluginId' failed to load", cause)
        data class UnsupportedVersion(val pluginId: String, val version: String, val minVersion: String) : PluginError("Plugin '$pluginId' requires version $version, minimum supported is $minVersion")
    }

    // ── File errors ──
    sealed class FileError(message: String, cause: Throwable? = null) : VibeCodingError(message, cause) {
        data class NotFound(val path: String) : FileError("File not found at '$path'")
        data class ReadFailed(val path: String, override val cause: Throwable) : FileError("Failed to read '$path'", cause)
        data class WriteFailed(val path: String, override val cause: Throwable) : FileError("Failed to write '$path'", cause)
        data class ParseFailed(val path: String, override val cause: Throwable) : FileError("Failed to parse '$path'", cause)
    }
}

fun VibeCodingError.toUserMessage(): String = when (this) {
    is VibeCodingError.ToolError.NotFound -> "Tool '${toolName}' is not available. Check the tool list."
    is VibeCodingError.ToolError.ExecutionFailed -> "Tool '${toolName}' failed: ${cause.message}"
    is VibeCodingError.ToolError.InvalidArgs -> "Invalid arguments for '${toolName}': ${validationErrors.joinToString("; ")}"
    is VibeCodingError.ToolError.PermissionDenied -> "Permission denied: $reason"
    is VibeCodingError.ToolError.ValidationError -> "Validation error in '${toolName}': $actual"
    is VibeCodingError.GenerationError.ModelNotFound -> "Model '$modelId' is not configured. Add it in Settings."
    is VibeCodingError.GenerationError.ProviderFailed -> "Provider '$providerName' returned an error: ${cause.message}"
    is VibeCodingError.GenerationError.ContextOverflow -> "Context limit reached ($contextUsed/$contextLimit tokens). Starting a new session may help."
    is VibeCodingError.GenerationError.DoomLoopDetected -> "I noticed a loop calling '$toolName'. Trying a different approach."
    is VibeCodingError.SecurityError.Blocked -> "Blocked for security: $pattern ($severity)"
    is VibeCodingError.SecurityError.Warning -> "Security warning: $pattern ($severity)"
    is VibeCodingError.FileError.NotFound -> "File '$path' was not found."
    is VibeCodingError.FileError.ReadFailed -> "Could not read '$path': ${cause.message}"
    is VibeCodingError.FileError.WriteFailed -> "Could not write '$path': ${cause.message}"
    is VibeCodingError.ConfigError.ParseError -> "Failed to parse configuration in '$configPath'."
    is VibeCodingError.AgentError.ExecutionFailed -> "Agent '$agentName' encountered an error: ${cause.message}"
    is VibeCodingError.AgentError.NotFound -> "Agent '$agentName' is not available."
    else -> message
}
