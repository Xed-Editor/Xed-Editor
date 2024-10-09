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

package io.github.rosemoe.sora.lsp.client

import android.util.Log


import io.github.rosemoe.sora.lsp.utils.toFileUri
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import java.net.URI
import java.util.concurrent.CompletableFuture


open class DefaultLanguageClient(protected val context: ClientContext) :
    LanguageClient {

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        /* boolean response = WorkspaceEditHandler.applyEdit(params.getEdit(), "LSP edits");*/
        // FIXME: Support it?
        return CompletableFuture.supplyAsync {
            ApplyWorkspaceEditResponse(
                false
            )
        }
    }

    override fun configuration(configurationParams: ConfigurationParams): CompletableFuture<List<Any>> {
        return super.configuration(configurationParams)
    }

    override fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>> {
        val workSpaceFolder = WorkspaceFolder()
        workSpaceFolder.uri =
            context.projectPath.toFileUri()
        // Always return the current project path
        return CompletableFuture.completedFuture(listOf(workSpaceFolder))
    }

    override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> {
        //Not prepared to support this feature
        return CompletableFuture.completedFuture(null)
    }

    override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> {
        // Not prepared to support this feature
        return CompletableFuture.completedFuture(null)
    }

    override fun telemetryEvent(o: Any) {
        Log.i(TAG, "telemetryEvent: $o")
    }

    override fun publishDiagnostics(publishDiagnosticsParams: PublishDiagnosticsParams) {
        // FIXME: support it.

        val diagnosticsContainer = context.project.diagnosticsContainer
        val uri = URI(publishDiagnosticsParams.uri).toFileUri()

        diagnosticsContainer.addDiagnostics(
            uri,
            publishDiagnosticsParams.diagnostics
        )

        val editor = context.getEditor(uri)

        editor?.onDiagnosticsUpdate()

    }

    override fun refreshDiagnostics(): CompletableFuture<Void> {
        // support it.
        return CompletableFuture.completedFuture(null)
    }

    override fun showMessage(messageParams: MessageParams) {
        context.eventListener.onShowMessage(messageParams)
    }

    override fun showMessageRequest(showMessageRequestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        return CompletableFuture.completedFuture(MessageActionItem())
    }

    override fun logMessage(messageParams: MessageParams) {
        context.eventListener.onLogMessage(messageParams)
    }

    companion object {
        private const val TAG = "DefaultLanguageClient"
    }
}