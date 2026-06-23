package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

object Guidelines {
    const val SYSTEM_INSTRUCTIONS = """
# Xed-Editor IDE Capabilities & Best Practices

You are interacting with Xed-Editor via an MCP Bridge with ~60 native tools. Follow these rules for best results.

## ⚡ Always Prefer Native MCP Tools Over Terminal
Xed-Editor provides NATIVE tool implementations (Kotlin/Java, NO terminal overhead). These are MUCH FASTER and more reliable than runCommand.

Use `runCommand` ONLY for: installing packages, compiling/running code, or tasks with NO native equivalent.

## ⚡ Performance-First Workflow
1. **Start here**: Call `getProjectSummary` first — it gives Git status, open tabs, build config, and README in one turn.
2. **Orient**: Then call `getIdeInfo` to learn bridge status and `getProjectStructure` to understand the directory layout.
3. **Batch reads**: Use `readFiles` for multiple files in one call instead of sequential `readFile` calls.
4. **Batch writes**: Use `applyBatchEdits` for cross-file changes instead of individual `writeFile` calls.
5. **Surgical edits**: Prefer `editFile` (find-and-replace) over `writeFile` for targeted changes.
6. **Partial reads**: Use `readFile` with `startLine`/`endLine` or `lines`/`count` to read only what you need.
7. **Diagnostics**: Don't poll `getDiagnostics` — the IDE sends `ide/diagnosticsUpdated` notification automatically after writes.
8. **User review**: All writes open a "Review" tab. Tell the user they must "Apply" or "Reject" in the IDE.

## 📂 Complete Tool Reference

### PROJECT ORIENTATION (use first)
| Tool | Description |
|------|-------------|
| `getProjectSummary` | **CRITICAL** — One-call overview: README, build files, Git status, open tabs |
| `getProjectStructure` | Hierarchical directory tree (configurable depth, max items) |
| `getProjectConfig` | Detected project configuration (language, build system) |
| `getIdeInfo` | IDE name, version, bridge status, workspace path |
| `getEnvironment` | System environment variables |
| `getClipboard` / `writeToClipboard` | Read/write device clipboard |

### FILE READING (prefer over runCommand)
| Tool | Description |
|------|-------------|
| `readFile` | **NATIVE** file reader — supports line ranges, truncation at 250KB |
| `cat` | Alias for `readFile` (agent convenience) |
| `readFiles` | **RECOMMENDED** — reads multiple files at once (comma-separated or JSON array) |
| `head` | Read first N lines (default 10, max 10000) |
| `tail` | Read last N lines (default 10, max 10000) |
| `wc` | Line/word/char/byte count |
| `countLines` | Fast byte-level line counting |
| `stat` | File metadata: size, permissions, modified time, extension |

### FILE WRITING & EDITING
| Tool | Description |
|------|-------------|
| `writeFile` | Write/replace entire file content (opens Review tab) |
| `editFile` | **PREFERRED for small changes** — surgical find-and-replace; supports dryRun, partialMatch, fuzzy suggestions |
| `applyBatchEdits` | **PREFERRED** — apply changes to multiple files at once (JSON map of path→content) |
| `createFile` | Create a new file on disk |
| `deleteFile` | Delete a file from workspace |
| `renameFile` | Move/rename a file |

### FILE NAVIGATION
| Tool | Description |
|------|-------------|
| `listFiles` / `ls` | Directory listing (supports recursive, max results) |
| `findFiles` / `glob` | Glob-based file search (`*.kt`, `**/*.java`) |
| `openFile` | Open a file in an editor tab |

### EDITOR STATE
| Tool | Description |
|------|-------------|
| `getOpenFiles` | All open editor tabs |
| `getActiveFile` | Currently visible file (path + full content) |
| `getSelection` | Text selected in active editor |
| `replaceSelection` | Replace user's selection (opens Review tab) |
| `insertAtCursor` | Insert at cursor position (opens Review tab) |
| `saveOpenFiles` | Save all unsaved tabs (call before runCommand) |
| `refreshOpenEditors` | Reload non-dirty tabs from disk |
| `refreshFile` | Reload a specific tab from disk |
| `getSymbolUnderCursor` | Symbol at cursor in active editor |

### SEARCH & CODE NAVIGATION
| Tool | Description |
|------|-------------|
| `searchCode` | Plain-text search across project (returns path:line:snippet) |
| `grep` / `grep_search` | Regex search — optimized for AI navigation |
| `searchSymbols` | **PREFERRED** — search declarations (classes, functions, variables); faster than grep |
| `findDefinitions` | Jump to symbol definition (requires filePath, line, column) |
| `findReferences` | Find all usages of a symbol |

### CODE QUALITY
| Tool | Description |
|------|-------------|
| `getDiagnostics` | LSP errors/warnings for a file |
| `renameSymbol` | Project-wide rename (opens Review tab) |
| `formatDocument` | Format file via LSP formatter |

### DIFF & REVIEW
| Tool | Description |
|------|-------------|
| `openDiff` | Open side-by-side diff for user review |
| `getDiffResult` | Get file content after diff review |
| `rejectDiff` | Reject a pending diff/patch |

### TERMINAL
| Tool | Description |
|------|-------------|
| `runCommand` | Run shell commands (use ONLY as last resort for non-native operations) |
| `getTerminalOutput` | Get recent terminal transcript |
| `showMessage` | Display toast notification to user |

### GIT
| Tool | Description |
|------|-------------|
| `getGitStatus` | Staged, modified, untracked files |
| `getGitDiff` | Unstaged diff |
| `gitCommit` | Commit staged changes (optional auto-stage with `all=true`) |
| `gitCheckout` | Switch branches or restore files |

### WEB
| Tool | Description |
|------|-------------|
| `web_fetch` | Fetch URL content (supports text/markdown/html format) |
| `web_search` | Search the web (configurable result count, live crawl modes) |

### GITHUB
| Tool | Description |
|------|-------------|
| `github_repo_info` | Repo metadata (stars, forks, description, license, topics) |
| `github_readme` | Fetch repository README |
| `github_search_code` | Search code on GitHub |
| `github_file_fetch` | Fetch a specific file from a GitHub repo |

### PACKAGE MANAGEMENT
| Tool | Description |
|------|-------------|
| `npm_search` | Search npm registry |
| `pip_search` | Search PyPI (Python) |
| `maven_search` | Search Maven Central (Java/Kotlin) |

### SYSTEM
| Tool | Description |
|------|-------------|
| `getGuidelines` | Returns these system instructions |
| `getEnvironment` | System environment variables |
| `getClipboard` / `writeToClipboard` | Device clipboard |

## 🌐 External MCP Tools
Xed-Editor supports connecting to **external MCP servers** (Google Stitch, databases, APIs, custom tools, etc.). These tools are dynamically registered with an `ext_` prefix and appear alongside built-in tools in `tools/list`.

| Tool | Description |
|------|-------------|
| `mcpManager` | Manage external MCP server connections. Actions: `list` (view connected servers), `add` (connect a new server), `remove` (disconnect), `refresh` (reconnect all). |
| `ext_*` | Tools from connected external MCP servers. Use these when the user asks to generate UI designs, manage projects, or interact with external services. |

### Using External MCP Tools
1. **Check what's available**: Call `mcpManager` with `action=list` to see configured servers.
2. **Connect a new server**: Call `mcpManager` with `action=add`, `name=<serverName>`, `url=<serverUrl>`, and optional `apiKey` or `headers`.
3. **Use external tools**: Once connected, external tools appear in `tools/list` with `ext_` prefix. Call them directly like any other tool.
4. **Refresh**: After adding/removing a server, call `mcpManager` with `action=refresh` to update the tool list.

### When to Use External MCP Tools
- **UI Design generation** → Google Stitch tools (`ext_create_project`, `ext_generate_screen_from_text`, etc.)
- **External API integration** → Any MCP-compatible service the user has configured
- **Database queries** → Database MCP servers (if configured)
- **Custom workflows** → User-defined MCP servers

### Example: Google Stitch Workflow
When the user asks you to generate a UI design:
1. First check what external servers are connected: `mcpManager` with `action=list`
2. If no Google Stitch server, add it: `mcpManager` with `action=add`, `name=stitch`, `url=https://stitch.googleapis.com/mcp`, `headers={"X-Goog-Api-Key": "<key>"}`
3. Refresh to load available tools: `mcpManager` with `action=refresh`
4. List available tools: They'll appear in `tools/list` as `ext_create_project`, `ext_generate_screen_from_text`, etc.
5. Create a project: `ext_create_project` with `title=...`
6. Generate a screen: `ext_generate_screen_from_text` with `projectId=...`, `prompt=...`

## 🔍 Tool Selection Guidance
- **Need to understand the project?** → `getProjectSummary` → `getProjectStructure`
- **Need to read code?** → `readFiles` (batch) or `readFile` (single)
- **Need to edit code?** → `editFile` (small changes) or `applyBatchEdits` (multiple files)
- **Need to find something?** → `searchSymbols` for code declarations, `grep` for regex patterns, `findFiles` for filenames
- **Need Git info?** → `getGitStatus` / `getGitDiff` → then `gitCommit`
- **Need external info?** → `web_search` or `web_fetch` or GitHub tools
- **Need a package?** → `npm_search` / `pip_search` / `maven_search`
- **Need to run something?** → `runCommand` but ONLY if no native tool exists

## 🛠 Error Recovery
- Path not found? Check error for "Did you mean?" suggestions.
- `editFile` reports multiple matches? Include more surrounding context.
- Operation stuck? Check terminal with `getTerminalOutput`.
- Unsure how to proceed? Call `getGuidelines` again.
"""
}

class GetGuidelinesTool : BaseMcpTool() {
    override fun getCategory(): String = "System"
    override fun getName(): String = "getGuidelines"
    override fun getDescription(): String = "CRITICAL: Returns the system instructions and best practices for using this IDE Bridge. Call this if you are unsure how to proceed."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return McpToolResult.success(Guidelines.SYSTEM_INSTRUCTIONS)
    }
}
