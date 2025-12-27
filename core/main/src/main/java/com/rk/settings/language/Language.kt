package com.rk.settings.language

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.application
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class to hold locale with its availability status
data class LocaleInfo(val locale: Locale, val isInstalled: Boolean, val tag: String, val displayName: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanguageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Single state for processed locale data
    val localeInfoList = remember { mutableStateOf<List<LocaleInfo>?>(null) }
    val currentLocale = LocalConfiguration.current.locales[0]

    // Load and process locales once
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val supportedLocales = readSupportedLocales(context)
            val installedTags = application?.resources?.assets?.locales?.toSet() ?: emptySet()

            // Process all data at once
            val processed =
                supportedLocales.map { locale ->
                    val tag = locale.toLanguageTag()
                    LocaleInfo(
                        locale = locale,
                        isInstalled = installedTags.contains(tag),
                        tag = tag,
                        displayName = "${locale.getDisplayLanguage(locale)} ($tag)",
                    )
                }
            localeInfoList.value = processed
        }
    }

    PreferenceLayout(
        label = stringResource(strings.lang),
        backArrowVisible = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://hosted.weblate.org/engage/xed-editor/".toUri())
                    )
                },
                text = { Text(stringResource(strings.translate)) },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            )
        },
    ) {
        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
            text = stringResource(strings.change_lang_warn),
            warning = true,
        )

        PreferenceGroup {
            val locales = localeInfoList.value

            if (locales != null) {
                locales.forEach { localeInfo ->
                    val isSelected = currentLocale.toLanguageTag() == localeInfo.tag

                    SettingsToggle(
                        modifier = Modifier,
                        label = localeInfo.displayName,
                        default = false,
                        sideEffect = { setAppLanguage(localeInfo.locale) },
                        showSwitch = false,
                        isEnabled = localeInfo.isInstalled,
                        startWidget = {
                            RadioButton(selected = isSelected, onClick = { setAppLanguage(localeInfo.locale) })
                        },
                    )
                }
            } else {
                SettingsToggle(
                    modifier = Modifier,
                    label = stringResource(strings.loading),
                    default = false,
                    sideEffect = {},
                    showSwitch = false,
                    startWidget = {},
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// Extract function outside composable to avoid recreation
private suspend fun readSupportedLocales(context: Context): List<Locale> =
    withContext(Dispatchers.IO) {
        return@withContext context.assets.open("supported_locales.json").use { stream ->
            val json = stream.bufferedReader().use { it.readText() }
            val localeStrings: List<String> = Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            localeStrings.map { Locale.forLanguageTag(it) }
        }
    }

fun setAppLanguage(locale: Locale) {
    val appLocale = LocaleListCompat.create(locale)
    AppCompatDelegate.setApplicationLocales(appLocale)
    Settings.current_lang = locale.toLanguageTag()
}
