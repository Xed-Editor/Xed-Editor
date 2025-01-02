package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.toLowerCase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.nio.charset.Charset
import java.util.Locale


object DefaultEncoding{
    val charsets = Charset.availableCharsets().map { it.value }
}

@Composable
fun DefaultEncoding(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.default_encoding), backArrowVisible = true) {
        var selectedEncoding by remember {
            mutableStateOf(PreferencesData.getString(PreferencesKeys.SELECTED_ENCODING, Charset.defaultCharset().name()))
        }
        val intraction = remember { MutableInteractionSource() }
        PreferenceGroup {
            PreferenceTemplate(
                modifier = modifier.clickable(
                    indication = ripple(),
                    interactionSource = intraction
                ) {
                    MainActivity.activityRef.get()?.adapter?.clearAllFragments()
                    selectedEncoding = Charset.defaultCharset().name()
                    PreferencesData.setString(PreferencesKeys.SELECTED_ENCODING, Charset.defaultCharset().name())
                },
                contentModifier = Modifier.fillMaxHeight(),
                title = { Text(fontWeight = FontWeight.Bold, text = Charset.defaultCharset().name()+" (Default)") },
                enabled = true,
                applyPaddings = true,
                startWidget = {
                    RadioButton(
                        selected = Charset.defaultCharset().name() == selectedEncoding, onClick = null
                    )
                }
            )

            val context = LocalContext.current

            DefaultEncoding.charsets.forEach { charset ->
                val intraction = remember { MutableInteractionSource() }
                if (charset.name().lowercase(Locale.getDefault()) != "utf-8") {
                    PreferenceTemplate(
                        modifier = modifier.clickable(
                            indication = ripple(),
                            interactionSource = intraction
                        ) {
                            MaterialAlertDialogBuilder(context).apply {
                                setTitle(strings.warning)
                                setMessage(strings.encoding_warning)
                                setPositiveButton(strings.ok){_,_ ->
                                    MainActivity.activityRef.get()?.adapter?.clearAllFragments()
                                    selectedEncoding = charset.name()
                                    PreferencesData.setString(PreferencesKeys.SELECTED_ENCODING,charset.name())
                                }
                                setNegativeButton(strings.cancel,null)
                                show()
                            }
                        },
                        contentModifier = Modifier.fillMaxHeight(),
                        title = { Text(fontWeight = FontWeight.Bold, text = charset.name()) },
                        enabled = true,
                        applyPaddings = true,
                        startWidget = {
                            RadioButton(
                                selected = charset.name() == selectedEncoding, onClick = null
                            )
                        }
                    )
                }
            }
        }

    }
}

