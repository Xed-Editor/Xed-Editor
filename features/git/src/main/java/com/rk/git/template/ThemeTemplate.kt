package com.rk.git.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.file.FileObject
import com.rk.icons.Error
import com.rk.icons.Icon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import org.json.JSONObject

object ThemeTemplate : GitTemplate(repoUrl = "https://github.com/Xed-Editor/Theme-Template") {

    override val id = "xed_theme"
    override val label = "Theme"
    override val description = "Create a new Xed-Editor theme"
    override val icon = Icon.ResourceIcon(drawables.palette)
    override val size: Long = 334848

    override val validConfiguration by derivedStateOf {
        idError == null && nameError == null && minAppVersionError == null
    }
    override val projectName by derivedStateOf { configStates.name }

    private var idError by mutableStateOf<String?>(null)
    private var nameError by mutableStateOf<String?>(null)
    private var minAppVersionError by mutableStateOf<String?>(null)

    private fun validateId(value: String): String? = if (value.isBlank()) strings.value_empty_err.getString() else null

    private fun validateName(value: String): String? = if (value.isBlank()) strings.name_empty_err.getString() else null

    private fun validateMinAppVersion(value: String): String? =
        if (value.toIntOrNull() == null) strings.value_invalid.getString() else null

    private var configStates by mutableStateOf(ConfigStates())

    private class ConfigStates {
        var id by mutableStateOf("com.rk.demo")
        var name by mutableStateOf("My Theme")
        var minAppVersion by mutableStateOf("87")
        var inheritBase by mutableStateOf(true)
    }

    @Composable
    override fun Configuration() {
        LaunchedEffect(Unit) {
            configStates = ConfigStates()
        }

        OutlinedTextField(
            value = configStates.id,
            onValueChange = {
                configStates.id = it
                idError = validateId(it)
            },
            label = { Text(stringResource(strings.template_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = idError != null,
            supportingText =
                idError?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            trailingIcon =
                idError?.let {
                    { Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
                },
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = configStates.name,
            onValueChange = {
                configStates.name = it
                nameError = validateName(it)
            },
            label = { Text(stringResource(strings.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameError != null,
            supportingText =
                nameError?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            trailingIcon =
                nameError?.let {
                    { Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
                },
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = configStates.minAppVersion,
            onValueChange = {
                configStates.minAppVersion = it
                minAppVersionError = validateMinAppVersion(it)
            },
            label = { Text(stringResource(strings.template_min_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = minAppVersionError != null,
            supportingText =
                minAppVersionError?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            trailingIcon =
                minAppVersionError?.let {
                    { Icon(XedIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
                },
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { configStates.inheritBase = !configStates.inheritBase }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = configStates.inheritBase,
                onCheckedChange = null,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = stringResource(strings.template_inherit_base_theme), style = MaterialTheme.typography.bodyLarge)
        }
    }

    override suspend fun afterClone(projectDir: FileObject) {
        val manifestFile = projectDir.getChild("manifest.json")
        if (manifestFile?.exists() == true) {
            val content = manifestFile.readText()
            val json = JSONObject(content)

            json.put("id", configStates.id)
            json.put("name", configStates.name)
            json.put("minAppVersion", configStates.minAppVersion.toIntOrNull() ?: 87)
            json.put("inheritBase", configStates.inheritBase)

            manifestFile.writeText(json.toString(2))
        }
    }
}
