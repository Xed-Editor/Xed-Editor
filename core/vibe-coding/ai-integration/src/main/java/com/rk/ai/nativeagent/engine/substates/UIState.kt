package com.rk.ai.nativeagent.engine

data class UIState(
    val commandCatalog: List<CommandCatalogEntry> = emptyList(),
    val dockOpen: Boolean = false,
    val dockClosing: Boolean = false,
    val compactionReason: String? = null,
)
