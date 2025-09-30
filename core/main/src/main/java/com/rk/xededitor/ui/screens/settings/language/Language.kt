package com.rk.xededitor.ui.screens.settings.language

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.google.gson.Gson
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.libcommons.application
import com.rk.resources.strings
import com.rk.xededitor.ui.components.SettingsToggle
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import java.util.Locale
import com.google.gson.reflect.TypeToken
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanguageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PreferenceLayout(label = stringResource(strings.lang),backArrowVisible = true, fab = {
        ExtendedFloatingActionButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://hosted.weblate.org/engage/xed-editor/")))
        }, text = {
            Text(stringResource(strings.translate))
        }, icon = {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        })
    }) {


        val languages = remember { mutableStateListOf<Locale>() }
        val indianLangs = remember { mutableStateListOf<Locale>() }

        LaunchedEffect(Unit) {
            fun readSupportedLocales(context: Context): List<Locale> {
                val json = context.assets.open("supported_locales.json").bufferedReader().use { it.readText() }
                val localeStrings: List<String> = Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
                return localeStrings.map { Locale.forLanguageTag(it) }
            }

            val langs = readSupportedLocales(context)

            val indiaList = mutableListOf<Locale>()
            val otherList = mutableListOf<Locale>()

            langs.forEach {
                if (
                    it.language == "en" ||
                    it.language == "ta" ||
                    it.language == "id" ||
                    it.toLanguageTag().endsWith("-IN")) {
                    indiaList.add(it)
                } else {
                    otherList.add(it)
                }
            }

            otherList.remove(Locale("hi"))
            indiaList.add(0,Locale("hi"))

            indianLangs.addAll(indiaList)
            languages.addAll(otherList)
        }


        val resources = context.resources
        val configuration = resources.configuration
        val localeList = configuration.locales
        val currentLocale = localeList[0]

        PreferenceGroup(heading = stringResource(strings.indian)) {
            if (indianLangs.isNotEmpty()){
                indianLangs.forEach { locale ->
                    SettingsToggle(
                        modifier = Modifier,
                        label = locale.getDisplayLanguage(locale),
                        default = false,
                        sideEffect = {
                            setAppLanguage(locale)
                        },
                        showSwitch = false,
                        startWidget = {
                            RadioButton(selected = currentLocale.language == locale.language, onClick = {
                                setAppLanguage(locale)
                            })
                        }
                    )
                }
            }else{
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

        PreferenceGroup(heading = stringResource(strings.other)) {
            if (languages.isNotEmpty()){
                languages.forEach { locale ->
                    SettingsToggle(
                        modifier = Modifier,
                        label = locale.getDisplayLanguage(locale),
                        default = false,
                        sideEffect = {
                            setAppLanguage(locale)
                        },
                        showSwitch = false,
                        startWidget = {
                            RadioButton(selected = currentLocale.language == locale.language, onClick = {
                                setAppLanguage(locale)
                            })
                        }
                    )
                }
            }else{
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
    Settings.currentLang = locale.language
}


