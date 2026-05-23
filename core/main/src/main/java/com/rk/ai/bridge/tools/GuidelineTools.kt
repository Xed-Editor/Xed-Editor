package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

object Guidelines {
    const val SYSTEM_INSTRUCTIONS = """
# Xed-Editor IDE Capabilities & Best Practices

You are interacting with Xed-Editor via an MCP Bridge. To provide the best experience, follow these rules:

## Performance First
1. **Orientation**: ALWAYS call `getProjectSummary` first. It is a compound tool that gives you Git status, open tabs, and project structure in one turn.
2. **Batching**: Use `readFiles` to read multiple files and `applyBatchEdits` to write multiple files. Avoid sequential one-by-one operations.
3. **Diagnostics**: Do not poll `getDiagnostics`. The IDE will send you a notification (`ide/diagnosticsUpdated`) automatically after a write if errors are found.

## Semantic Search
- Prefer `searchSymbols` over `searchCode`.
- Use `findDefinitions` and `findReferences` for precise code navigation.

## Reliability
- If a path is not found, check the error message for "Did you mean?" suggestions.
- Use `getTerminalOutput` to see the current state of the integrated terminal instead of guessing.

## User Interaction
- All file writes open a "Review" tab for the user. Inform the user that they need to "Apply" or "Reject" the changes in the IDE.
"""
}

class GetGuidelinesTool : BaseMcpTool() {
    override val name: String = "getGuidelines"
    override val description: String = "CRITICAL: Returns the system instructions and best practices for using this IDE Bridge. Call this if you are unsure how to proceed."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return resultText(Guidelines.SYSTEM_INSTRUCTIONS)
    }
}
