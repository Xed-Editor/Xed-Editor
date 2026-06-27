package com.rk.settings.extension

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.App
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.extension.extensionManager
import com.rk.extension.Extension
import com.rk.resources.strings

@Composable
fun ExtensionSettings(extension: Extension?) {
    val api = extensionManager.loadedExtensions[extension]?.api

    PreferenceLayout(label = extension?.name ?: stringResource(strings.ext_not_found)) {
        if (extension == null || api == null) {
            Text(stringResource(strings.ext_not_found_desc), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            api.SettingsContent()
        }
    }
}
