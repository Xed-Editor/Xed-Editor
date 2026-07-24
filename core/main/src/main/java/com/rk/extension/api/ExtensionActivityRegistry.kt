package com.rk.extension.api

import java.util.UUID

internal object ExtensionActivityRegistry {
    private val screens = mutableMapOf<String, ExtensionScreen>()

    fun register(screen: ExtensionScreen): String {
        val id = UUID.randomUUID().toString()
        screens[id] = screen
        return id
    }

    fun unregister(id: String) {
        screens.remove(id)
    }

    fun getScreen(id: String): ExtensionScreen? = screens[id]
}
