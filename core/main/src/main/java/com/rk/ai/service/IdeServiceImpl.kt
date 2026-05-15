package com.rk.ai.service

import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.IdeNotificationSender
import com.rk.file.FileWrapper
import java.io.File
import kotlinx.coroutines.launch

class IdeServiceImpl(
    private val viewModel: MainViewModel,
    private val notificationSender: IdeNotificationSender? = null
) : IdeService {

    private val tabRepo = object : TabRepository {
        override val tabs get() = viewModel.tabs
        override val currentTab get() = viewModel.currentTab
        override val tabManager get() = viewModel.tabManager
    }
    private val scope = object : ScopeProvider {
        override val viewModelScope get() = viewModel.viewModelScope
    }
    private val fileOpener = object : FileOpener {
        override fun openFileInEditor(file: File, switchToTab: Boolean) {
            viewModel.viewModelScope.launch {
                viewModel.editorManager.openFile(FileWrapper(file), projectRoot = null, switchToTab = switchToTab)
            }
        }
    }

    private val fileService = FileService(tabRepo)
    private val editorService = EditorService(tabRepo, scope, fileOpener, notificationSender)
    private val lspService = LspService(tabRepo, scope)
    private val gitService = GitService()
    private val terminalService = TerminalService()
    private val terminalManagementService = TerminalManagementService()
    private val clipboardService = ClipboardService()
    private val settingsService = SettingsService()
    private val projectService = ProjectService(tabRepo, viewModel)

    override fun resolvePath(path: String): File? = fileService.resolvePath(path)
    override fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String> = fileService.listFiles(directory, recursive, maxFiles)
    override suspend fun getFileContent(filePath: String, startLine: Int?, endLine: Int?): String? = fileService.getFileContent(filePath, startLine, endLine)
    override suspend fun writeFile(file: File, content: String) {
        fileService.writeFile(file, content)
        viewModel.viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            val diags = lspService.getDiagnostics(file.absolutePath)
            if (diags.size() > 0) {
                notificationSender?.sendNotification("ide/diagnosticsUpdated", JsonObject().apply {
                    addProperty("filePath", file.absolutePath)
                    add("diagnostics", diags)
                })
            }
        }
    }
    override fun refreshEditors(filePath: String?, force: Boolean) = fileService.refreshEditors(filePath, force)
    override suspend fun createFile(filePath: String, content: String?): String = fileService.createFile(filePath, content)
    override suspend fun deleteFile(filePath: String): String = fileService.deleteFile(filePath)
    override suspend fun renameFile(sourcePath: String, destPath: String): String = fileService.renameFile(sourcePath, destPath)

    override fun openFile(file: File) = editorService.openFile(file)
    override suspend fun getOpenFiles(): List<JsonObject> = editorService.getOpenFiles()
    override suspend fun getActiveFile(): JsonObject? = editorService.getActiveFile()
    override suspend fun getSelection(): String = editorService.getSelection()
    override fun replaceSelection(newContent: String) = editorService.replaceSelection(newContent)
    override fun insertAtCursor(newContent: String) = editorService.insertAtCursor(newContent)
    override suspend fun saveAllFiles(): String = editorService.saveAllFiles()
    override fun showPatch(filePath: String, oldContent: String, newContent: String, title: String, onApply: suspend () -> Unit) =
        editorService.showPatch(filePath, oldContent, newContent, title, onApply)
    override fun applyBatchEdits(edits: Map<String, String>, title: String) =
        editorService.applyBatchEdits(edits, title)
    override fun rejectPatch(filePath: String) = editorService.rejectPatch(filePath)
    override fun showMessage(message: String) = editorService.showMessage(message)
    override fun ensureIdeEnabled() = editorService.ensureIdeEnabled()
    override fun closeTab(filePath: String): String = editorService.closeTab(filePath)

    override suspend fun getDiagnostics(filePath: String): JsonArray = lspService.getDiagnostics(filePath)
    override suspend fun findDefinitions(filePath: String, line: Int, column: Int): JsonArray = lspService.findDefinitions(filePath, line, column)
    override suspend fun findReferences(filePath: String, line: Int, column: Int): JsonArray = lspService.findReferences(filePath, line, column)
    override fun renameSymbol(filePath: String, line: Int, column: Int, newName: String) = lspService.renameSymbol(filePath, line, column, newName)
    override suspend fun formatDocument(filePath: String) = lspService.formatDocument(filePath)
    override suspend fun formatSelection(filePath: String): String = lspService.formatSelection(filePath)

    override suspend fun getGitStatus(workspacePath: String): JsonObject = gitService.getGitStatus(workspacePath)
    override suspend fun getGitDiff(workspacePath: String): String = gitService.getGitDiff(workspacePath)
    override suspend fun gitCommit(workspacePath: String, message: String, all: Boolean): String = gitService.gitCommit(workspacePath, message, all)
    override suspend fun gitCheckout(workspacePath: String, target: String, createNew: Boolean): String = gitService.gitCheckout(workspacePath, target, createNew)
    override suspend fun gitLog(workspacePath: String, maxCount: Int): JsonArray = gitService.gitLog(workspacePath, maxCount)
    override suspend fun listGitBranches(workspacePath: String): JsonObject = gitService.listGitBranches(workspacePath)
    override suspend fun gitPull(workspacePath: String, rebase: Boolean): String = gitService.gitPull(workspacePath, rebase)
    override suspend fun gitPush(workspacePath: String, force: Boolean): String = gitService.gitPush(workspacePath, force)
    override suspend fun gitFetch(workspacePath: String): String = gitService.gitFetch(workspacePath)
    override suspend fun gitCreateBranch(workspacePath: String, branchName: String, startPoint: String?): String = gitService.gitCreateBranch(workspacePath, branchName, startPoint)
    override suspend fun gitStash(workspacePath: String, message: String?): String = gitService.gitStash(workspacePath, message)
    override suspend fun gitStashPop(workspacePath: String): String = gitService.gitStashPop(workspacePath)

    override suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult = terminalService.runCommand(command, timeoutSeconds)
    override suspend fun getTerminalOutput(lines: Int?): String = terminalService.getTerminalOutput(lines)

    override fun getPrimaryWorkspacePath(): String = projectService.getPrimaryWorkspacePath()
    override suspend fun searchCode(query: String, limit: Int, path: String?, isRegex: Boolean): JsonArray = projectService.searchCode(query, limit, path, isRegex)
    override suspend fun searchSymbols(query: String, limit: Int, path: String?): JsonArray = projectService.searchSymbols(query, limit, path)
    override suspend fun findFiles(query: String, limit: Int, path: String?): JsonArray = projectService.findFiles(query, limit, path)
    override suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String = projectService.getProjectStructure(path, maxDepth, maxItems)
    override suspend fun getProjectConfig(workspacePath: String): JsonObject = projectService.getProjectConfig(workspacePath)
    override suspend fun getSymbolUnderCursor(): JsonObject = projectService.getSymbolUnderCursor()

    override suspend fun listSessions(): JsonArray = terminalManagementService.listSessions()
    override suspend fun createSession(name: String, workingDir: String): String = terminalManagementService.createSession(name, workingDir)
    override suspend fun killSession(sessionId: String): String = terminalManagementService.killSession(sessionId)
    override suspend fun writeToSession(sessionId: String, text: String): String = terminalManagementService.writeToSession(sessionId, text)
    override suspend fun getSessionOutput(sessionId: String, lines: Int?): String = terminalManagementService.getSessionOutput(sessionId, lines)

    override suspend fun getClipboard(): String = clipboardService.getClipboard()
    override fun setClipboard(text: String) = clipboardService.setClipboard(text)

    override fun getSetting(key: String): String? = settingsService.getSetting(key)
    override fun setSetting(key: String, value: String) = settingsService.setSetting(key, value)
    override fun getAllSettings(): JsonObject = settingsService.getAllSettings()

    override suspend fun toggleBookmark(filePath: String, line: Int): String {
        return "bookmarks not supported by this editor"
    }
    override suspend fun listBookmarks(): JsonArray {
        return JsonArray()
    }
}