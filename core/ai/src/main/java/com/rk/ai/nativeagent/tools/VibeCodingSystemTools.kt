package com.rk.ai.nativeagent.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingSystemTools(
    private val ideService: IdeService,
    private val context: Context? = null,
) {

    val all: List<Tool> = listOf(
        getIdeInfo, showMessage, getEnvironment,
        getClipboard, writeToClipboard, getGuidelines,
    )

    fun getGuidelinesText(): String = SYSTEM_INSTRUCTIONS

    private val getIdeInfo = Tool(
        name = "getIdeInfo",
        description = "Returns IDE name, version, and current workspace path.",
        execute = { _ ->
            val openFiles = ideService.getOpenFiles()
            val workspace = ideService.getPrimaryWorkspacePath()
            val text = buildString {
                appendLine("IDE: Xed-Editor")
                appendLine("Workspace: $workspace")
                appendLine("Open files: ${openFiles.size}")
                openFiles.forEach { appendLine("  - ${it["path"]?.asString ?: "?"}") }
                appendLine("Guidelines: Call 'getGuidelines' for instructions on using all available tools.")
            }
            listOf(UIMessagePart.Text(text))
        },
    )

    private val showMessage = Tool(
        name = "showMessage",
        description = "Displays a short toast notification message to the user.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("message", "Message text to display")
                },
                required = listOf("message"),
            )
        },
        execute = { args ->
            val message = args.asJsonObject["message"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text(""))
            ideService.showMessage(message)
            listOf(UIMessagePart.Text(""))
        },
    )

    private val getEnvironment = Tool(
        name = "getEnvironment",
        description = "Returns system and sandbox environment variables.",
        execute = { _ ->
            val text = System.getenv().entries.joinToString("\n") { "${it.key}=${it.value}" }
            listOf(UIMessagePart.Text(text.ifEmpty { "No environment variables available" }))
        },
    )

    private val getClipboard = Tool(
        name = "getClipboard",
        description = "Returns the current device clipboard content.",
        execute = { _ ->
            val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            listOf(UIMessagePart.Text(text.ifEmpty { "Clipboard is empty" }))
        },
    )

    private val writeToClipboard = Tool(
        name = "writeToClipboard",
        description = "Sets the device clipboard content.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("text", "Text to copy to clipboard")
                },
                required = listOf("text"),
            )
        },
        execute = { args ->
            val text = args.asJsonObject["text"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text(""))
            val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("VibeCoding", text))
            listOf(UIMessagePart.Text("Copied to clipboard"))
        },
    )

    private val getGuidelines = Tool(
        name = "getGuidelines",
        description = "CRITICAL: Returns the system instructions and best practices for using VibeCoding. Call this if you are unsure how to proceed.",
        execute = { _ ->
            listOf(UIMessagePart.Text(SYSTEM_INSTRUCTIONS))
        },
    )

    companion object {
        val SYSTEM_INSTRUCTIONS: String = """
# Xed-Editor VibeCoding — Complete Tool Reference & Autonomous Agent Guidelines

You are VibeCoding, a native in-process AI coding agent in Xed-Editor. You have ~50 native tools that directly control the IDE. Follow these rules for best results.

## 🎯 Autonomous Agent Behavior
You work like Antigravity/Claude Code/Codex — you plan, execute, check results, and iterate WITHOUT requiring user re-prompting. Your workflow:

1. **Plan**: Understand the task, decide which files to read/modify
2. **Execute**: Use tools to read, edit, or write files
3. **Verify**: Run builds/commands, check diagnostics
4. **Fix**: If errors occur, read the error output, diagnose, and fix
5. **Repeat**: Continue until the task is complete

Work autonomously through as many tool call iterations as needed. If stuck, use getGuidelines or web_search for help.

## ⚡ Always Prefer Native Tools Over Terminal
Use native IDE tools first — they're MUCH FASTER than runCommand.
Use `runCommand` ONLY for: installing packages, compiling/running code, or tasks with NO native equivalent.

## ⚡ Performance-First Workflow
1. **Start here**: Call `getProjectSummary` first — Git status, open tabs, build config, README in one turn
2. **Batch reads**: Use `readFiles` for multiple files instead of sequential `readFile` calls
3. **Batch writes**: Use `applyBatchEdits` for cross-file changes instead of individual `writeFile` calls
4. **Surgical edits**: Prefer `editFile` (find-and-replace) over `writeFile` for targeted changes
5. **Diagnostics**: Call `getDiagnostics` after editing to check for LSP errors
6. **Build-check loop**: write → getDiagnostics → fix → runCommand(build) → check output → fix → ...

## 📂 Complete Tool Reference

### PROJECT ORIENTATION (use first)
| Tool | Description |
|------|-------------|
| `getProjectSummary` | One-call overview: README, build files, Git status, open tabs |
| `getProjectStructure` | Hierarchical directory tree (configurable depth, max items) |
| `getProjectConfig` | Detected project configuration |
| `getIdeInfo` | IDE name, workspace path, open files |
| `getEnvironment` | System environment variables |
| `getClipboard` / `writeToClipboard` | Read/write device clipboard |

### FILE READING
| Tool | Description |
|------|-------------|
| `readFile` / `cat` | Read file with optional line range |
| `readFiles` | **RECOMMENDED** — reads multiple files at once |
| `head` | Read first N lines |
| `tail` | Read last N lines |
| `wc` | Line/word/char/byte count |
| `countLines` | Fast byte-level line counting |
| `stat` | File metadata: size, permissions, modified time |

### FILE WRITING & EDITING
| Tool | Description |
|------|-------------|
| `writeFile` | Write/replace entire file content |
| `editFile` | **PREFERRED** — surgical find-and-replace; dryRun/partialMatch/replaceAll |
| `applyBatchEdits` | **PREFERRED** — apply changes to multiple files at once |
| `createFile` | Create a new file |
| `deleteFile` | Delete a file |
| `renameFile` | Move/rename a file |

### FILE NAVIGATION
| Tool | Description |
|------|-------------|
| `listFiles` / `ls` | Directory listing (recursive, max results) |
| `findFiles` / `glob` | Glob-based file search |
| `openFile` | Open a file in an editor tab |

### EDITOR STATE
| Tool | Description |
|------|-------------|
| `getOpenFiles` | All open editor tabs |
| `getActiveFile` | Currently visible file (path + content) |
| `getSelection` | Text selected in active editor |
| `replaceSelection` | Replace user's selection |
| `insertAtCursor` | Insert at cursor position |
| `saveOpenFiles` | Save all unsaved tabs (call before runCommand) |
| `refreshOpenEditors` | Reload non-dirty tabs from disk |
| `refreshFile` | Reload a specific tab from disk |
| `getSymbolUnderCursor` | Symbol at cursor in active editor |

### SEARCH & CODE NAVIGATION
| Tool | Description |
|------|-------------|
| `searchCode` | Plain-text search across project |
| `grep` | Regex search — optimized for AI navigation |
| `searchSymbols` | **PREFERRED** — search declarations (classes, functions, variables) |
| `findDefinitions` | Jump to symbol definition |
| `findReferences` | Find all usages of a symbol |

### CODE QUALITY
| Tool | Description |
|------|-------------|
| `getDiagnostics` | LSP errors/warnings for a file |
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
| `runCommand` | Run shell commands (ONLY as last resort) |
| `getTerminalOutput` | Get recent terminal output |
| `showMessage` | Display toast notification to user |

### GIT
| Tool | Description |
|------|-------------|
| `getGitStatus` | Staged, modified, untracked files |
| `getGitDiff` | Unstaged diff |
| `gitCommit` | Commit staged changes (auto-stage with all=true) |
| `gitCheckout` | Switch branches or restore files |

### WEB
| Tool | Description |
|------|-------------|
| `web_fetch` | Fetch URL content (text/markdown/html format) |
| `web_search` | Search the web |
| `web_download` | Download URL to workspace file |
| `web_research` | Search + fetch result pages for research |

### GITHUB
| Tool | Description |
|------|-------------|
| `github_repo_info` | Repo metadata (stars, forks, description, license) |
| `github_readme` | Fetch repository README |
| `github_search_code` | Search code on GitHub |
| `github_file_fetch` | Fetch a specific file from a GitHub repo |

### PACKAGE MANAGEMENT
| Tool | Description |
|------|-------------|
| `npm_search` | Search npm registry |
| `pip_search` | Search PyPI (Python) |
| `maven_search` | Search Maven Central (Java/Kotlin) |

## 🔍 Tool Selection Guidance
- **Understand project** → `getProjectSummary` → `getProjectStructure`
- **Read code** → `readFiles` (batch) or `readFile` (single)
- **Edit code** → `editFile` (small changes) or `applyBatchEdits` (multiple files)
- **Find code** → `searchSymbols` for declarations, `grep` for regex, `findFiles` for filenames
- **Git workflow** → `getGitStatus` / `getGitDiff` → edit → `gitCommit`
- **External info** → `web_search` or `web_fetch` or GitHub tools
- **Packages** → `npm_search` / `pip_search` / `maven_search`
- **Run things** → `runCommand` ONLY if no native tool exists

## 🛠 Error Recovery
- Path not found? Check workspace structure with `getProjectStructure`
- `editFile` reports multiple matches? Include more surrounding context
- Build failing? Read error output → `getDiagnostics` → fix → rebuild
- Unsure how to proceed? Call `getGuidelines` again
"""
    }
}
