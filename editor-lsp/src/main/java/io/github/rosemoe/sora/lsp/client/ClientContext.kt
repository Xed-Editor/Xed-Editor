/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
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


import io.github.rosemoe.sora.lsp.client.languageserver.requestmanager.RequestManager
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.utils.FileUri


/**
 * The client context which is received by [DefaultLanguageClient]. The context contain
 * information about the runtime and its components.
 *
 * **Note:** This is an internal API and may change without notice.
 *
 * @author dingyi
 *
 */
interface ClientContext {
    /**
     * Returns the [LspEditor] for the given document URI.
     */
    fun getEditor(documentUri: FileUri): LspEditor?

    /**
     * Returns the project path associated with the LanguageClient.
     */
    val projectPath: FileUri

    val project: LspProject

    /**
     * Returns the [RequestManager] associated with the Language Server Connection.
     */
    val requestManager: RequestManager?
    val eventListener: EventHandler.EventListener
}