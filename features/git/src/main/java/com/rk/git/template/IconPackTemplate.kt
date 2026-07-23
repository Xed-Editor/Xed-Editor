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
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings
import org.json.JSONObject

object IconPackTemplate : GitTemplate(repoUrl = "https://github.com/Xed-Editor/Icon-Template") {

    override val id = "xed_icon_pack"
    override val label = "Icon pack"
    override val description = "Create a new Xed-Editor icon pack"
    override val icon = Icon.ResourceIcon(drawables.widgets)
    override val size: Long = 18432

    override val validConfiguration by mutableStateOf(true)
    override val projectName by derivedStateOf { configStates.name }

    private var configStates by mutableStateOf(ConfigStates())

    private class ConfigStates {
        var id by mutableStateOf("my-icons")
        var name by mutableStateOf("My Icon Pack")
        var minAppVersion by mutableStateOf("87")
        var applyTint by mutableStateOf(false)
    }

    @Composable
    override fun Configuration() {
        LaunchedEffect(Unit) {
            configStates = ConfigStates()
        }

        OutlinedTextField(
            value = configStates.id,
            onValueChange = { configStates.id = it },
            label = { Text(stringResource(strings.template_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = configStates.name,
            onValueChange = { configStates.name = it },
            label = { Text(stringResource(strings.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = configStates.minAppVersion,
            onValueChange = { configStates.minAppVersion = it },
            label = { Text(stringResource(strings.template_min_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { configStates.applyTint = !configStates.applyTint }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = configStates.applyTint,
                onCheckedChange = null,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = stringResource(strings.template_apply_tint), style = MaterialTheme.typography.bodyLarge)
        }
    }

    override suspend fun afterClone(projectDir: FileObject) {
        val manifestFile = projectDir.getChildForName("manifest.json")
        if (manifestFile.exists()) {
            val content = manifestFile.readText() ?: return
            val json = JSONObject(content)

            json.put("id", configStates.id)
            json.put("name", configStates.name)
            json.put("minAppVersion", configStates.minAppVersion.toIntOrNull() ?: 87)
            json.put("applyTint", configStates.applyTint)

            manifestFile.writeText(json.toString(2))
        }
    }
}
