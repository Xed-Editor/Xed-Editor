package com.rk.lsp

import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider

fun interface ConnectionProviderFactory {
    fun create(): StreamConnectionProvider
}

sealed interface LspConnectionConfig {
    fun providerFactory(): ConnectionProviderFactory

    data class Socket(
        val host: String = "localhost",
        val port: Int
    ) : LspConnectionConfig {
        override fun providerFactory() = ConnectionProviderFactory { SocketStreamConnectionProvider(port, host) }
    }

    data class Process(
        val command: Array<String>
    ) : LspConnectionConfig {
        override fun providerFactory() = ConnectionProviderFactory { ProcessConnection(command) }
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

    data class Custom(
        val provider: StreamConnectionProvider
    ) : LspConnectionConfig {
        override fun providerFactory() = ConnectionProviderFactory { provider }
    }
}