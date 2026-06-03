package com.rk.ai.agent

import com.rk.ai.service.IdeService
import com.rk.ai.models.UIMessage
import com.rk.ai.core.MessageRole
import com.rk.ai.models.UIMessagePart

data class WorkspaceSnapshot(
    val workspaceRoot: String = "",
    val projectRoot: String = "",
    val activeFile: String = "",
    val openFiles: List<String> = emptyList(),
    val selection: String = "",
    val cursorPosition: Int = -1,
    val gitBranch: String = "",
    val gitChanges: Int = 0,
    val projectLanguage: String = "",
    val buildSystem: String = "",
    val diagnosticCount: Int = 0,
) {
    fun isEmpty(): Boolean = workspaceRoot.isBlank()

    fun toContextMessage(): UIMessage = UIMessage.system(
        buildContextBlock()
    )

    fun buildContextBlock(): String = buildString {
        appendLine("<workspace_context>")
        if (workspaceRoot.isNotBlank()) appendLine("  workspace_root: $workspaceRoot")
        if (projectRoot.isNotBlank()) appendLine("  project_root: $projectRoot")
        if (activeFile.isNotBlank()) appendLine("  active_file: $activeFile")
        if (openFiles.isNotEmpty()) {
            appendLine("  open_files:")
            openFiles.forEach { appendLine("    - $it") }
        }
        if (selection.isNotBlank()) appendLine("  selection: $selection")
        if (cursorPosition >= 0) appendLine("  cursor_position: line $cursorPosition")
        if (gitBranch.isNotBlank()) appendLine("  git_branch: $gitBranch")
        if (gitChanges > 0) appendLine("  git_uncommitted: $gitChanges")
        if (projectLanguage.isNotBlank()) appendLine("  project_language: $projectLanguage")
        if (buildSystem.isNotBlank()) appendLine("  build_system: $buildSystem")
        if (diagnosticCount > 0) appendLine("  lsp_diagnostics: $diagnosticCount")
        appendLine("</workspace_context>")
    }
}

class WorkspaceContextCollector(
    private val ideService: IdeService,
) {
    suspend fun snapshot(): WorkspaceSnapshot {
        return try {
            val workspaceRoot = ideService.getPrimaryWorkspacePath()
            if (workspaceRoot.isNullOrBlank()) return WorkspaceSnapshot()

            val projectConfig = try {
                ideService.getProjectConfig(workspaceRoot)
            } catch (_: Exception) { com.google.gson.JsonObject() }

            val gitStatus = try {
                ideService.getGitStatus(workspaceRoot)
            } catch (_: Exception) { com.google.gson.JsonObject() }

            val activeFileData = try {
                ideService.getActiveFile()
            } catch (_: Exception) { null }

            val openFilesData = try {
                ideService.getOpenFiles()
            } catch (_: Exception) { emptyList() }

            val selectionData = try {
                ideService.getSelection()
            } catch (_: Exception) { null }

            val diagnostics = try {
                activeFileData?.let { ideService.getDiagnostics(activeFileData["path"]?.asString ?: "") }
            } catch (_: Exception) { null }

            WorkspaceSnapshot(
                workspaceRoot = workspaceRoot,
                projectRoot = projectConfig["projectDir"]?.asString ?: workspaceRoot,
                activeFile = activeFileData?.let {
                    it["path"]?.asString ?: it["filePath"]?.asString ?: ""
                } ?: "",
                openFiles = openFilesData.mapNotNull { f ->
                    f["path"]?.asString ?: f["filePath"]?.asString
                },
                selection = selectionData?.asString ?: "",
                cursorPosition = activeFileData?.let {
                    it["cursorLine"]?.asInt ?: -1
                } ?: -1,
                gitBranch = gitStatus["branch"]?.asString ?: "",
                gitChanges = gitStatus["changes"]?.asJsonArray?.size() ?: 0,
                projectLanguage = projectConfig["language"]?.asString ?: "",
                buildSystem = projectConfig["buildSystem"]?.asString ?: "",
                diagnosticCount = diagnostics?.asJsonArray?.size() ?: 0,
            )
        } catch (_: Exception) {
            WorkspaceSnapshot()
        }
    }

    fun buildContextMessage(snapshot: WorkspaceSnapshot): String {
        if (snapshot.isEmpty()) return ""
        return snapshot.buildContextBlock()
    }
}
