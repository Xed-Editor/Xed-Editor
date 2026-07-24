package com.rk.components

import androidx.compose.runtime.Composable

fun interface DialogProvider {
    @Composable fun Content()
}

object DialogRegistry {

    private val providers = mutableListOf<DialogProvider>()

    fun register(provider: DialogProvider) {
        providers += provider
    }

    fun unregister(provider: DialogProvider) {
        providers -= provider
    }

    @Composable
    fun getDialogs(): List<@Composable () -> Unit> {
        return providers.map { { it.Content() } }
    }
}
