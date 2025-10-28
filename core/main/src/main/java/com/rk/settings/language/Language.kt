package com.rk.settings.language

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.components.SettingsToggle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanguageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PreferenceLayout(
        label = stringResource(strings.lang),
        backArrowVisible = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://hosted.weblate.org/engage/xed-editor/".toUri()
                        )
                    )
                },
                text = {
                    Text(stringResource(strings.translate))
                },
                icon = {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            )
        }
    ) {

        InfoBlock(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning, contentDescription = null
                )
            },
            text = stringResource(strings.change_lang_warn),
            warning = true
        )

        val languages = remember { mutableStateListOf<Locale>() }

        LaunchedEffect(Unit) {
            fun readSupportedLocales(context: Context): List<Locale> {
                val json = context.assets.open("supported_locales.json")
                    .bufferedReader()
                    .use { it.readText() }
                val localeStrings: List<String> = Gson().fromJson(
                    json,
                    object : TypeToken<List<String>>() {}.type
                )
                return localeStrings.map { Locale.forLanguageTag(it) }
            }

            val langs = readSupportedLocales(context)
            languages.addAll(langs)
        }

        val configuration = LocalConfiguration.current
        val localeList = configuration.locales
        val currentLocale = localeList[0]

        PreferenceGroup(heading = stringResource(strings.lang)) {
            if (languages.isNotEmpty()) {
                languages.forEach { locale ->

                    SettingsToggle(
                        modifier = Modifier,
                        label = "${locale.getDisplayLanguage(locale)} (${locale.toLanguageTag()})",
                        default = false,
                        sideEffect = {
                            setAppLanguage(locale)
                        },
                        showSwitch = false,
                        startWidget = {
                            RadioButton(
                                selected = currentLocale.toLanguageTag() == locale.toLanguageTag(),
                                onClick = {
                                    setAppLanguage(locale)
                                }
                            )
                        }
                    )
                }
            } else {
                SettingsToggle(
                    modifier = Modifier,
                    label = stringResource(strings.loading),
                    default = false,
                    sideEffect = {},
                    showSwitch = false,
                    startWidget = {}
                )
            }
        }
    }
}

fun setAppLanguage(locale: Locale) {
    val appLocale = LocaleListCompat.create(locale)
    AppCompatDelegate.setApplicationLocales(appLocale)
    Settings.currentLang = locale.toLanguageTag()
}