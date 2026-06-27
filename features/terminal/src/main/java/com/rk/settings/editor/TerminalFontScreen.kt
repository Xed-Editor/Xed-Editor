package com.rk.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.resources.strings
import com.rk.settings.Settings

@Composable
fun TerminalFontScreen(modifier: Modifier = Modifier) {
    val selectedFontPath = Settings.terminal_font_path
    val etcFontExists = sandboxDir().child("etc/font.ttf").exists()
    val warningMessage = if (etcFontExists) stringResource(strings.terminal_font_warning) else null

    FontScreen(modifier, selectedFontPath, DEFAULT_TERMINAL_FONT_PATH, warningMessage) { font ->
        Settings.terminal_font_path = font.pathOrAsset
        Settings.is_terminal_font_asset = font.isAsset
    }
}
