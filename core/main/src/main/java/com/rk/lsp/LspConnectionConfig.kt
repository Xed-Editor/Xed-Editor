package com.rk.lsp

import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider

fun interface ConnectionProviderFactory {
    fun create(): StreamConnectionProvider
}

sealed interface LspConnectionConfig {
    fun toFactory(): ConnectionProviderFactory

    /**
     * Connect via TCP socket (e.g., for locally running language servers).
     */
    data class Socket(
        val host: String = "localhost",
        val port: Int
    ) : LspConnectionConfig {
        override fun toFactory() = ConnectionProviderFactory { SocketStreamConnectionProvider(port, host) }
    }

    /**
     * Connect by launching an external process (e.g., `node server.js`, `java -jar lsp.jar`).
     */
    data class Process(
        val command: Array<String>
    ) : LspConnectionConfig {
        override fun toFactory() = ConnectionProviderFactory { ProcessConnection(command) }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Process

            return command.contentEquals(other.command)
        }

        override fun hashCode(): Int {
            return command.contentHashCode()
        }
    }

    /**
     * Use a pre-existing or custom [StreamConnectionProvider].
     */
    data class Custom(
        val provider: StreamConnectionProvider
    ) : LspConnectionConfig {
        override fun toFactory() = ConnectionProviderFactory { provider }
    }
}