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
import java.nio.charset.Charset
import java.util.Locale

object DefaultEncoding {
    val charsets = Charset.availableCharsets().map { it.value }
}

@Composable
fun DefaultEncoding(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.default_encoding), backArrowVisible = true) {
        var selectedEncoding by remember { mutableStateOf(Settings.encoding) }
        val interaction = remember { MutableInteractionSource() }

        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
            text = strings.encoding_warning.getString(),
            warning = true,
        )

        PreferenceGroup {
            PreferenceTemplate(
                modifier =
                    modifier.clickable(indication = ripple(), interactionSource = interaction) {
                        // MainActivity.instance?.adapter?.clearAllFragments()
                        selectedEncoding = Charset.defaultCharset().name()
                        Settings.encoding = selectedEncoding
                    },
                contentModifier = Modifier.fillMaxHeight(),
                title = {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = Charset.defaultCharset().name() + " (${stringResource(strings.default_option)})",
                    )
                },
                enabled = true,
                applyPaddings = true,
                startWidget = {
                    RadioButton(selected = Charset.defaultCharset().name() == selectedEncoding, onClick = null)
                },
            )

            DefaultEncoding.charsets.forEach { charset ->
                val interaction = remember { MutableInteractionSource() }
                if (charset.name().lowercase(Locale.getDefault()) != "utf-8") {
                    PreferenceTemplate(
                        modifier =
                            modifier.clickable(indication = ripple(), interactionSource = interaction) {
                                // MainActivity.instance?.adapter?.clearAllFragments()
                                selectedEncoding = charset.name()
                                Settings.encoding = charset.name()
                            },
                        contentModifier = Modifier.fillMaxHeight(),
                        title = { Text(fontWeight = FontWeight.Bold, text = charset.name()) },
                        enabled = true,
                        applyPaddings = true,
                        startWidget = { RadioButton(selected = charset.name() == selectedEncoding, onClick = null) },
                    )
                }
            }
        }
    }
}
