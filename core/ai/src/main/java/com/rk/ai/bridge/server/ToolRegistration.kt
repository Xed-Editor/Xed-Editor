package com.rk.ai.bridge.server

import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.tools.*

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
}
