package com.rk.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import io.github.rosemoe.sora.text.LineSeparator
import kotlinx.coroutines.launch

enum class LineEnding(val label: String, val value: String, val char: String, val type: LineSeparator) {
    LF("LF (Linux)", "lf", "\n", LineSeparator.LF),
    CR("CR (macOS)", "cr", "\r", LineSeparator.CR),
    CRLF("CRLF (Windows)", "crlf", "\r\n", LineSeparator.CRLF);

    fun applyOn(text: String): String {
        return text.replace(REPLACE_REGEX, char)
    }

    companion object {
        private val REPLACE_REGEX = "(\\r\\n|\\r|\\n)".toRegex()

        fun fromValue(value: String): LineEnding? {
            return when (value) {
                "lf" -> LF
                "cr" -> CR
                "crlf" -> CRLF
                else -> null
            }
        }

        fun detect(text: String): LineEnding {
            return when {
                text.contains(CRLF.char) -> CRLF
                text.contains(CR.char) -> CR
                else -> LF
            }
        }
    }
}

@Composable
fun DefaultLineEnding(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    PreferenceLayout(label = stringResource(strings.line_ending), backArrowVisible = true) {
        var selectedEnding by remember { mutableStateOf(Settings.line_ending) }

        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
            text = strings.line_ending_info.getString(),
        )

        PreferenceGroup {
            LineEnding.entries.forEach { ending ->
                val interaction = remember { MutableInteractionSource() }
                PreferenceTemplate(
                    modifier =
                        modifier.clickable(indication = ripple(), interactionSource = interaction) {
                            selectedEnding = ending.value
                            Settings.line_ending = ending.value
                            scope.launch { refreshEditorSettings() }
                        },
                    contentModifier = Modifier.fillMaxHeight(),
                    title = { Text(fontWeight = FontWeight.Bold, text = ending.label) },
                    enabled = true,
                    applyPaddings = true,
                    startWidget = { RadioButton(selected = ending.value == selectedEnding, onClick = null) },
                )
            }
        }
    }
}
