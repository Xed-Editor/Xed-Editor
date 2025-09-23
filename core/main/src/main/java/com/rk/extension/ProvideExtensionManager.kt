package com.rk.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalExtensionManager = staticCompositionLocalOf<ExtensionManager> {
    error("No local provided for ExtensionManager")
}

@Composable
fun ProvideExtensionManager(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val extensionManager = remember { ExtensionManager(context) }

    CompositionLocalProvider(
        LocalExtensionManager provides extensionManager,
        content = content
    )
}
