/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2023  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.lsp.client.languageserver.requestmanager

import io.github.rosemoe.sora.lsp.client.languageserver.ServerStatus
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.LanguageServerWrapper
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture


/**
 * Default implementation for LSP requests/notifications handling.
 */
class DefaultRequestManager(
    val wrapper: LanguageServerWrapper, val server: LanguageServer, val client: LanguageClient,
    val serverCapabilities: ServerCapabilities
) : RequestManager() {

    private val textDocumentOptions: TextDocumentSyncOptions? =
        if (serverCapabilities.textDocumentSync.isRight) serverCapabilities.textDocumentSync.right else TextDocumentSyncOptions().apply {
            change = serverCapabilities.textDocumentSync.left
            openClose = true
            save = Either.forLeft(true)
        }
    private val workspaceService: WorkspaceService =
        server.workspaceService
    private val textDocumentService: TextDocumentService = server.textDocumentService

    // Client
    override fun showMessage(messageParams: MessageParams) {
        client.showMessage(messageParams)
    }

    override fun showMessageRequest(showMessageRequestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        return client.showMessageRequest(showMessageRequestParams)
    }

    override fun logMessage(messageParams: MessageParams) {
        client.logMessage(messageParams)
    }

    override fun telemetryEvent(o: Any) {
        client.telemetryEvent(o)
    }

    override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> {
        return client.registerCapability(params)
    }

    override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> {
        return client.unregisterCapability(params)
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        return client.applyEdit(params)
    }

    override fun publishDiagnostics(publishDiagnosticsParams: PublishDiagnosticsParams) {
        client.publishDiagnostics(publishDiagnosticsParams)
    }

    // Server
    // General
    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult>? {
        return if (checkStatus()) {
            try {
                server.initialize(params)
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else {
            null
        }
    }

    override fun initialized(params: InitializedParams) {
        if (wrapper.status == ServerStatus.STARTED) {
            try {
                server.initialized(params)
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun shutdown(): CompletableFuture<Any>? {
        return if (checkStatus()) {
            try {
                server.shutdown()
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun exit() {
        if (checkStatus()) {
            try {
                server.exit()
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun getTextDocumentService(): TextDocumentService? {
        return if (checkStatus()) {
            try {
                textDocumentService
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun getWorkspaceService(): WorkspaceService? {
        return if (checkStatus()) {
            try {
                workspaceService
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else {
            null
        }
    }

    // Workspace service
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        if (checkStatus()) {
            try {
                workspaceService.didChangeConfiguration(params)
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        if (checkStatus()) {
            try {
                workspaceService.didChangeWatchedFiles(params)
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        if (checkStatus()) {
            try {
                workspaceService.didChangeWorkspaceFolders(params)
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol?>>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.workspaceSymbolProvider.right != null) workspaceService.symbol(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.executeCommandProvider != null) workspaceService.executeCommand(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    // Text document service
    override fun didOpen(params: DidOpenTextDocumentParams) {
        if (checkStatus()) {
            try {
                if (textDocumentOptions?.openClose == true) {
                    textDocumentService.didOpen(params)
                }
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        if (checkStatus()) {
            try {
                if (textDocumentOptions?.change != null) {
                    textDocumentService.didChange(params)
                }
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun willSave(params: WillSaveTextDocumentParams) {
        if (checkStatus()) {
            try {
                if (textDocumentOptions?.willSave == true) {
                    textDocumentService.willSave(params)
                }
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun willSaveWaitUntil(params: WillSaveTextDocumentParams): CompletableFuture<List<TextEdit>>? {
        return if (checkStatus()) {
            try {
                if (textDocumentOptions?.willSaveWaitUntil == true) textDocumentService.willSaveWaitUntil(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        if (checkStatus()) {
            try {
                if (textDocumentOptions?.save != null) {
                    textDocumentService.didSave(params)
                }
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        if (checkStatus()) {
            try {
                if (textDocumentOptions?.openClose == true) {
                    textDocumentService.didClose(params)
                }
            } catch (e: Exception) {
                crashed(e)
            }
        }
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.completionProvider != null) textDocumentService.completion(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.completionProvider.resolveProvider == true) textDocumentService.resolveCompletionItem(
                    unresolved
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun hover(params: TextDocumentPositionParams): CompletableFuture<Hover>? {
        return hover(HoverParams(params.textDocument, params.position))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.hoverProvider.right != null) textDocumentService.hover(params) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun signatureHelp(params: TextDocumentPositionParams): CompletableFuture<SignatureHelp>? {
        return signatureHelp(SignatureHelpParams(params.textDocument, params.position))
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.signatureHelpProvider != null) textDocumentService.signatureHelp(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location?>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.referencesProvider.right != null) textDocumentService.references(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun documentHighlight(params: TextDocumentPositionParams): CompletableFuture<List<DocumentHighlight>>? {
        return documentHighlight(DocumentHighlightParams(params.textDocument, params.position))
    }

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentHighlightProvider != null) textDocumentService.documentHighlight(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentSymbolProvider != null) textDocumentService.documentSymbol(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentFormattingProvider != null) textDocumentService.formatting(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else {
            null
        }
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentRangeFormattingProvider != null) textDocumentService.rangeFormatting(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentOnTypeFormattingProvider != null) textDocumentService.onTypeFormatting(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun definition(params: TextDocumentPositionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>? {
        return definition(DefinitionParams(params.textDocument, params.position))
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.definitionProvider.right != null) textDocumentService.definition(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>>? {
        return if (checkStatus()) {
            try {
                if (checkCodeActionProvider(serverCapabilities.codeActionProvider)) textDocumentService.codeAction(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.codeLensProvider != null) textDocumentService.codeLens(params) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens>? {
        return if (checkStatus()) {
            try {
                val codeLensProvider = serverCapabilities.codeLensProvider
                if (codeLensProvider != null && codeLensProvider.resolveProvider != null && codeLensProvider.resolveProvider) textDocumentService.resolveCodeLens(
                    unresolved
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun documentLink(params: DocumentLinkParams): CompletableFuture<List<DocumentLink>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentLinkProvider != null) textDocumentService.documentLink(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun documentLinkResolve(unresolved: DocumentLink): CompletableFuture<DocumentLink>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.documentLinkProvider != null && serverCapabilities
                        .documentLinkProvider.resolveProvider
                ) textDocumentService.documentLinkResolve(unresolved) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit>? {
        //        if (checkStatus()) {
        //            try {
        //                return (checkProvider((Either<Boolean, StaticRegistrationOptions>)serverCapabilities.getRenameProvider())) ?
        //                        textDocumentService.rename(params) :
        //                        null;
        //            } catch (Exception e) {
        //                crashed(e);
        //                return null;
        //            }
        //        }
        return null
    }

    override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>? {
        return null
    }

    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>? {
        return null
    }

    override fun documentColor(params: DocumentColorParams): CompletableFuture<List<ColorInformation>>? {
        return null
    }

    override fun colorPresentation(params: ColorPresentationParams): CompletableFuture<List<ColorPresentation>>? {
        return null
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>>? {
        return if (checkStatus()) {
            try {
                if (serverCapabilities.foldingRangeProvider != null) textDocumentService.foldingRange(
                    params
                ) else null
            } catch (e: Exception) {
                crashed(e)
                null
            }
        } else null
    }

    fun checkStatus(): Boolean {
        return wrapper.status == ServerStatus.INITIALIZED
    }

    private fun crashed(e: Exception) {
        e.printStackTrace(System.err)
        wrapper.crashed(e)
    }

    private fun checkCodeActionProvider(provider: Either<Boolean, CodeActionOptions?>?): Boolean {
        return provider != null && (provider.isLeft && provider.left || (provider.isRight
                && provider.right != null))
    }
}