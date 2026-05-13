package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

object Guidelines {
    const val SYSTEM_INSTRUCTIONS = """
# Xed-Editor IDE Capabilities & Best Practices

You are interacting with Xed-Editor via an MCP Bridge. To provide the best experience, follow these rules:

## ⚡ CRITICAL: Always Prefer Native MCP Tools Over Terminal
Xed-Editor provides NATIVE tool implementations (Kotlin/Java, NO terminal overhead) for common operations. These are MUCH FASTER than running terminal commands via `runCommand`.

| Operation | Use THIS native tool | NOT runCommand(...) |
|-----------|---------------------|---------------------|
| Read file | `readFile` or `cat` | `cat file` |
| Search text | `searchCode` or `grep` | `grep -r` |
| Find files | `findFiles` or `glob` | `find **/*.kt` |
| List directory | `listFiles` or `ls` | `ls -la` |
| Word/line count | `wc` | `wc -l` |
| First N lines | `head` | `head -n 20` |
| Last N lines | `tail` | `tail -n 20` |
| File metadata | `stat` | `stat \| ls -la` |
| Line count | `countLines` | `wc -l` |
| Diagnostics | `getDiagnostics` | parsing LSP by hand |
| Symbol search | `searchSymbols` | `grep class` |

Use `runCommand` ONLY for: installing packages, compiling/running code, git operations not covered by `gitCommit`/`gitCheckout`, or anything with NO native equivalent.

## ⚡ Performance First
1. **Orientation**: ALWAYS call `getProjectSummary` first. It is a compound tool that gives you Git status, open tabs, and project structure in one turn.
2. **Batching**: Use `readFiles` to read multiple files and `applyBatchEdits` to write multiple files. Avoid sequential one-by-one operations.
3. **Surgical edits**: Use `editFile` to make targeted changes (find-and-replace) instead of rewriting the entire file with writeFile.
4. **Diagnostics**: Do not poll `getDiagnostics`. The IDE will send you a notification (`ide/diagnosticsUpdated`) automatically after a write if errors are found.
5. **File reading**: Use `readFile` with `startLine`/`endLine` or `lines`/`count` params to read only what you need instead of whole files.

## 🔍 Semantic Search
- Prefer `searchSymbols` over `searchCode`.
- Use `findDefinitions` and `findReferences` for precise code navigation.

## 🛠 Reliability
- If a path is not found, check the error message for \"Did you mean?\" suggestions.
- If `editFile` reports multiple matches, include more context from surrounding lines to make a unique match.
- Use `getTerminalOutput` to see the current state of the integrated terminal instead of guessing.

## 🤝 User Interaction
- All file writes open a \"Review\" tab for the user. Inform the user that they need to \"Apply\" or \"Reject\" the changes in the IDE.
"""
}

class GetGuidelinesTool : BaseMcpTool() {
    override fun getName(): String = "getGuidelines"
    override fun getDescription(): String = "CRITICAL: Returns the system instructions and best practices for using this IDE Bridge. Call this if you are unsure how to proceed."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(Guidelines.SYSTEM_INSTRUCTIONS)
    }
}