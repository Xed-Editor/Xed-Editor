package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.rk.resources.strings
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.nio.charset.Charset


object DefaultEncoding{
    val charsets = Charset.availableCharsets().map { it.value }
}

@Composable
fun DefaultEncoding(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.default_encoding), backArrowVisible = true) {
        PreferenceGroup {
            DefaultEncoding.charsets.forEach { charset ->
                val interactionSource = remember { MutableInteractionSource() }
                PreferenceTemplate(
                    modifier = modifier.clickable(
                        indication = ripple(),
                        interactionSource = interactionSource
                    ) {
                        // onclick
                    },
                    contentModifier = Modifier.fillMaxHeight(),
                    title = { Text(fontWeight = FontWeight.Bold, text = charset.name()) },
                    enabled = true,
                    applyPaddings = true,
                    startWidget = {
                        RadioButton(
                            selected = true, onClick = null
                        )
                    }
                )
            }
        }

    }
}

