/*
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
 */
package io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition

import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider

/**
 * Creates new instance with the given language id which is different from the file extension.
 *
 * @param ext             The extension.
 * @param languageIds     The language server ids mapping to extension(s).
 * @param connectProvider The connect provider.
 */
open class CustomLanguageServerDefinition
    (
    ext: String, languageIds: Map<String, String>, serverConnectProvider: ServerConnectProvider
) : LanguageServerDefinition() {
    protected var serverConnectProvider: ServerConnectProvider

    init {
        this.ext = ext
        this.languageIds = languageIds
        this.serverConnectProvider = serverConnectProvider
    }

    /**
     * Creates new instance.
     *
     * @param ext             The extension.
     * @param serverConnectProvider The connect provider.
     */
    @Suppress("unused")
    constructor(ext: String, serverConnectProvider: ServerConnectProvider) : this(
        ext, emptyMap(), serverConnectProvider
    )

    override fun toString(): String {
        return "CustomLanguageServerDefinition : $serverConnectProvider"
    }

    override fun createConnectionProvider(workingDir: String): StreamConnectionProvider {
        return serverConnectProvider.createConnectionProvider(workingDir)
    }

    override fun equals(other: Any?): Boolean {
        if (other is CustomLanguageServerDefinition) {
            return other.serverConnectProvider == serverConnectProvider
        }
        return false
    }

    override fun hashCode(): Int {
        return ext.hashCode() + 3 * serverConnectProvider.hashCode()
    }

    fun interface ServerConnectProvider {
        /**
         * Creates a StreamConnectionProvider given the working directory
         *
         * @param workingDir The root directory
         * @return The stream connection provider
         */
        fun createConnectionProvider(workingDir: String): StreamConnectionProvider
    }
}
