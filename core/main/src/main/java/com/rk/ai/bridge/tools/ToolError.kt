package com.rk.ai.bridge.tools

sealed class ToolError(val code: Int, override val message: String) : Exception(message) {
    class MissingParam(name: String) : ToolError(-32602, "Missing required param: $name")
    class PathOutsideWorkspace(path: String) : ToolError(-32002, "Path outside workspace: $path")
    class InvalidParam(name: String, detail: String = "") : ToolError(-32602, "Invalid param '$name'${
        if (detail.isNotBlank()) ": $detail" else ""
    }")
    class ToolTimeout(name: String) : ToolError(-32000, "Tool '$name' timed out")
    class Internal(override val cause: Throwable) : ToolError(-32603, "${cause::class.java.simpleName}: ${cause.message ?: "internal error"}")
}
