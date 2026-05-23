package com.rk.tabs.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class AiTerminalSheetState

@Composable
fun rememberAiTerminalSheetState(): AiTerminalSheetState {
    return remember { AiTerminalSheetState() }
}
