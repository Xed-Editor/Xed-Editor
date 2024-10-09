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

import android.util.Pair
import androidx.annotation.WorkerThread
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


/**
 * A customizable connection provider, where callers provide the input and output streams.
 */
class CustomConnectProvider(private val streamProvider: StreamProvider) : StreamConnectionProvider {
    private lateinit var _inputStream: InputStream
    private lateinit var _outputStream: OutputStream

    override fun start() {
        val streams = streamProvider.getStreams()
        _inputStream = streams.first
        _outputStream = streams.second
    }

    override val inputStream: InputStream
        get() = _inputStream

    override val outputStream: OutputStream
        get() = _outputStream

    override fun close() {
        try {
            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            // ignore
        }
    }

    /**
     * Provider of language server connection
     */
    interface StreamProvider {
        @WorkerThread
        fun getStreams(): Pair<InputStream, OutputStream>
    }
}

