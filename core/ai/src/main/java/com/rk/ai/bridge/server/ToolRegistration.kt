package com.rk.ai.bridge.server

import com.rk.ai.bridge.AnnotatedTool
import com.rk.ai.bridge.McpTool
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.ToolAnnotations
import com.rk.ai.bridge.tools.*

private val READ_ONLY = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
private val READ_WRITE = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false)
private val DESTRUCTIVE = ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false)
private val DESTRUCTIVE_IDEMPOTENT = ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false)
private val OPEN_WORLD = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true)
private val OPEN_WORLD_READONLY = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true)

private val ANNOTATION_MAP: Map<String, ToolAnnotations> = mapOf(
    // System (read-only)
    "getIdeInfo" to READ_ONLY,
    "getGuidelines" to READ_ONLY,
    "showMessage" to READ_ONLY,
    "getEnvironment" to READ_ONLY,
    "getClipboard" to READ_ONLY,
    "writeToClipboard" to READ_WRITE,
    "getProjectConfig" to READ_ONLY,

    // File Operations
    "readFile" to READ_ONLY,
    "cat" to READ_ONLY,
    "readFiles" to READ_ONLY,
    "writeFile" to DESTRUCTIVE,
    "listFiles" to READ_ONLY,
    "ls" to READ_ONLY,
    "openFile" to READ_ONLY,
    "createFile" to DESTRUCTIVE_IDEMPOTENT,
    "deleteFile" to DESTRUCTIVE,
    "renameFile" to DESTRUCTIVE,
    "moveFile" to DESTRUCTIVE,
    "createDirectory" to DESTRUCTIVE_IDEMPOTENT,
    "mkdir" to DESTRUCTIVE_IDEMPOTENT,
    "head" to READ_ONLY,
    "tail" to READ_ONLY,
    "wc" to READ_ONLY,
    "countLines" to READ_ONLY,
    "stat" to READ_ONLY,
    "editFile" to DESTRUCTIVE,
    "applyBatchEdits" to DESTRUCTIVE,

    // Editor
    "getOpenFiles" to READ_ONLY,
    "getActiveFile" to READ_ONLY,
    "getSelection" to READ_ONLY,
    "replaceSelection" to DESTRUCTIVE,
    "insertAtCursor" to DESTRUCTIVE,
    "saveOpenFiles" to READ_WRITE,
    "refreshOpenEditors" to READ_ONLY,
    "refreshFile" to READ_ONLY,
    "getSymbolUnderCursor" to READ_ONLY,

    // Search (read-only)
    "searchCode" to READ_ONLY,
    "grep" to READ_ONLY,
    "grepSearch" to READ_ONLY,
    "searchSymbols" to READ_ONLY,
    "findFiles" to READ_ONLY,
    "glob" to READ_ONLY,
    "semanticSearch" to OPEN_WORLD_READONLY,

    // Code Intelligence / LSP (read-only)
    "getDiagnostics" to READ_ONLY,
    "findDefinitions" to READ_ONLY,
    "findReferences" to READ_ONLY,
    "renameSymbol" to DESTRUCTIVE,
    "formatDocument" to READ_WRITE,

    // Git
    "getGitStatus" to READ_ONLY,
    "getGitDiff" to READ_ONLY,
    "gitCommit" to DESTRUCTIVE,
    "gitCheckout" to DESTRUCTIVE,

    // Diff
    "openDiff" to READ_WRITE,
    "getDiffResult" to READ_ONLY,
    "rejectDiff" to READ_ONLY,

    // Terminal
    "runCommand" to DESTRUCTIVE,
    "getTerminalOutput" to READ_ONLY,

    // Web (open world)
    "webFetch" to OPEN_WORLD_READONLY,
    "webSearch" to OPEN_WORLD_READONLY,
    "webDownload" to OPEN_WORLD,
    "webResearch" to OPEN_WORLD_READONLY,

    // GitHub (open world)
    "githubRepoInfo" to OPEN_WORLD_READONLY,
    "githubReadme" to OPEN_WORLD_READONLY,
    "githubSearchCode" to OPEN_WORLD_READONLY,
    "githubFileFetch" to OPEN_WORLD_READONLY,

    // Package Management (open world)
    "npmSearch" to OPEN_WORLD_READONLY,
    "pipSearch" to OPEN_WORLD_READONLY,
    "mavenSearch" to OPEN_WORLD_READONLY,

    // Project
    "getProjectStructure" to READ_ONLY,
    "getProjectSummary" to READ_ONLY,

    // AI tools
    "codeReview" to READ_ONLY,
    "testGenerator" to READ_ONLY,
    "contextManager" to READ_ONLY,
    "taskPlanner" to READ_ONLY,
    "docGenerator" to READ_ONLY,
    "codebaseIndexer" to READ_ONLY,
    "customInstructions" to READ_ONLY,
    "agentWorkflow" to READ_ONLY,
    "planMode" to READ_ONLY,
    "dependencyAnalyzer" to READ_ONLY,

    // External MCP
    "mcpManager" to READ_ONLY,
)

fun McpTool.withAnnotations(): McpTool {
    val ann = ANNOTATION_MAP[getName()] ?: when {
        getCategory().startsWith("AI") -> READ_ONLY
        else -> null
    }
    return if (ann != null) AnnotatedTool(this, ann) else this
}

fun registerBuiltInTools(registry: McpToolRegistry) {
    registry.apply {
        register(GetIdeInfoTool()); register(GetGuidelinesTool())
        register(ReadFileTool()); register(CatTool())
        register(ReadFilesTool()); register(WriteFileTool())
        register(ListFilesTool()); register(LsTool())
        register(OpenFileTool())
        register(GetOpenFilesTool()); register(GetActiveFileTool())
        register(GetSelectionTool()); register(ReplaceSelectionTool()); register(InsertAtCursorTool())
        register(SaveOpenFilesTool()); register(RefreshOpenEditorsTool()); register(RefreshFileTool())
        register(OpenDiffTool()); register(GetDiffResultTool()); register(RejectDiffTool())
        register(RunCommandTool()); register(ShowMessageTool())
        register(GetEnvironmentTool()); register(GetClipboardTool()); register(WriteToClipboardTool())
        register(SearchCodeTool()); register(GrepTool()); register(GrepSearchTool())
        register(SearchSymbolsTool())
        register(FindFilesTool()); register(GlobTool())
        register(HeadTool()); register(TailTool()); register(WcTool())
        register(CountLinesTool()); register(StatTool())
        register(GetDiagnosticsTool()); register(FindDefinitionsTool()); register(FindReferencesTool())
        register(RenameSymbolTool()); register(FormatDocumentTool())
        register(GetGitStatusTool()); register(GetGitDiffTool()); register(GitCommitTool()); register(GitCheckoutTool())
        register(CreateFileTool()); register(DeleteFileTool()); register(RenameFileTool()); register(MoveFileTool())
        register(CreateDirectoryTool()); register(MkdirTool())
        register(ApplyBatchEditsTool()); register(EditFileTool())
        register(GetTerminalOutputTool())
        register(GetProjectStructureTool()); register(GetProjectSummaryTool())
        register(GetSymbolUnderCursorTool()); register(GetProjectConfigTool())
        register(WebFetchTool()); register(WebSearchTool()); register(WebDownloadTool()); register(WebResearchTool())
        register(GitHubRepoInfoTool()); register(GitHubReadmeTool())
        register(GitHubSearchCodeTool()); register(GitHubFileFetchTool())
        register(NpmSearchTool()); register(PipSearchTool()); register(MavenSearchTool())
        register(CodeReviewTool()); register(TestGeneratorTool())
        register(ContextManagerTool()); register(TaskPlannerTool())
        register(DocGeneratorTool()); register(CodebaseIndexerTool())
        register(SemanticSearchTool())
        register(CustomInstructionsTool()); register(AgentWorkflowTool())
        register(PlanModeTool()); register(DependencyAnalyzerTool())
        register(ExternalMcpConfigTool())
    }

    val names = registry.listNames().toList()
    for (name in names) {
        val tool = registry.get(name) ?: continue
        val annotated = tool.withAnnotations()
        if (annotated !== tool) {
            registry.register(annotated)
        }
    }
}
