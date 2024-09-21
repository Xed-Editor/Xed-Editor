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

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CodeAction
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
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture


/**
 * Base representation of currently supported LSP-based requests and notifications.
 *
 * **Some features are not directly supported in the sora-editor.**
 *
 * (e.g. viewing method signatures),
 *  consider to remove them or abstract additional classes for the caller to provide support
 *
 */
abstract class RequestManager : LanguageClient, TextDocumentService, WorkspaceService,
    LanguageServer {
    //------------------------------------- Server2Client ---------------------------------------------------------//
    abstract override fun showMessage(messageParams: MessageParams)
    abstract override fun showMessageRequest(showMessageRequestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem>

    abstract override fun logMessage(messageParams: MessageParams)
    abstract override fun telemetryEvent(o: Any)
    abstract override fun registerCapability(params: RegistrationParams): CompletableFuture<Void>

    abstract override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void>

    abstract override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse>
    abstract override fun publishDiagnostics(publishDiagnosticsParams: PublishDiagnosticsParams)
    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        return super.semanticTokensFull(params)
    }

    override fun semanticTokensFullDelta(params: SemanticTokensDeltaParams): CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> {
        return super.semanticTokensFullDelta(params)
    }

    override fun semanticTokensRange(params: SemanticTokensRangeParams): CompletableFuture<SemanticTokens> {
        return super.semanticTokensRange(params)
    }

    //--------------------------------------Client2Server-------------------------------------------------------------//
    // General
    abstract override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult>?
    abstract override fun initialized(params: InitializedParams)
    abstract override fun shutdown(): CompletableFuture<Any>?
    abstract override fun exit()

    // Workspace Service
    abstract override fun didChangeConfiguration(params: DidChangeConfigurationParams)
    abstract override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams)
    abstract override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any>?

    // Text Document Service
    abstract override fun didOpen(params: DidOpenTextDocumentParams)
    abstract override fun didChange(params: DidChangeTextDocumentParams)
    abstract override fun willSave(params: WillSaveTextDocumentParams)
    abstract override fun willSaveWaitUntil(params: WillSaveTextDocumentParams): CompletableFuture<List<TextEdit>>?
    abstract override fun didSave(params: DidSaveTextDocumentParams)
    abstract override fun didClose(params: DidCloseTextDocumentParams)
    abstract override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>>?
    abstract override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem>?
    abstract override fun hover(params: HoverParams): CompletableFuture<Hover>?

    @Deprecated("")
    abstract fun hover(params: TextDocumentPositionParams): CompletableFuture<Hover>?

    @Deprecated("")
    abstract fun signatureHelp(params: TextDocumentPositionParams): CompletableFuture<SignatureHelp>?
    abstract override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp>?
    abstract override fun references(params: ReferenceParams): CompletableFuture<List<Location?>>?

    @Deprecated("")
    abstract fun documentHighlight(params: TextDocumentPositionParams): CompletableFuture<List<DocumentHighlight>>?
    abstract override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>>?
    abstract override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>>?
    abstract override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>>?
    abstract override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>>?
    abstract override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>>?

    @Deprecated("")
    abstract fun definition(params: TextDocumentPositionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>?
    abstract override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>?
    abstract override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>>?
    abstract override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>>?
    abstract override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens>?
    abstract override fun documentLink(params: DocumentLinkParams): CompletableFuture<List<DocumentLink>>?
    abstract override fun documentLinkResolve(unresolved: DocumentLink): CompletableFuture<DocumentLink>?
    abstract override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit>?
    abstract override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>?
    abstract override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>>?
    abstract override fun documentColor(params: DocumentColorParams): CompletableFuture<List<ColorInformation>>?
    abstract override fun colorPresentation(params: ColorPresentationParams): CompletableFuture<List<ColorPresentation>>?
    abstract override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>>?
}

