package com.rk.xededitor.ui.activities.main

data class Command(
    val id: String,
    val prefix: String? = null,
    val label: String,
    val description: String? = null,
    val action: (MainViewModel) -> Unit,
    val keybinds: String? = null
)