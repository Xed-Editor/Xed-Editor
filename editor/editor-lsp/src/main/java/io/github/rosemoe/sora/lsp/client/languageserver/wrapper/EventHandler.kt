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

package io.github.rosemoe.sora.lsp.client.languageserver.wrapper

import io.github.rosemoe.sora.lsp.client.languageserver.ServerInitializeListener
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.services.LanguageServer
import java.util.function.BooleanSupplier
import java.util.function.Function


/**
 * A language server and client event handler.
 */
class EventHandler internal constructor(
    private val listener: EventListener,
    private val isRunning: BooleanSupplier
) :
    Function<MessageConsumer, MessageConsumer> {
    private var languageServer: LanguageServer? = null
    override fun apply(messageConsumer: MessageConsumer): MessageConsumer {
        return MessageConsumer { message: Message ->
            if (isRunning.asBoolean) {
                handleMessage(message)
                messageConsumer.consume(message)
            }
        }
    }

    private fun handleMessage(message: Message) {
        if (message is ResponseMessage) {
            if (message.result is InitializeResult) {
                listener.initialize(languageServer, message.result as InitializeResult)
            }
        }
    }

    fun setLanguageServer(languageServer: LanguageServer) {
        this.languageServer = languageServer
    }

    interface EventListener : ServerInitializeListener {
        override fun initialize(server: LanguageServer?, result: InitializeResult) {
           // do nothing
        }

        fun onHandlerException(exception: Exception) {}
        fun onShowMessage(messageParams: MessageParams?) {}
        fun onLogMessage(messageParams: MessageParams?) {}

        companion object {
            val DEFAULT: EventListener = object : EventListener {}
        }
    }
}

