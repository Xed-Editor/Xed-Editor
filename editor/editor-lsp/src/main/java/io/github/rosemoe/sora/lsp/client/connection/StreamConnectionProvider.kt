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

package io.github.rosemoe.sora.lsp.client.connection

import androidx.annotation.WorkerThread
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


interface StreamConnectionProvider : Closeable {
    /**
     * Connect to the server.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun start()

    /**
     * Get the input stream of the connection.
     */
    val inputStream: InputStream

    /**
     * Get the output stream of the connection.
     */
    val outputStream: OutputStream

    /**
     * Close the connection.
     */
    override fun close()
}