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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import org.json.JSONObject

class ThemeTemplate : GitTemplate(repoUrl = "https://github.com/Xed-Editor/Theme-Template") {

    override val id = "xed_theme"
    override val label = "Theme"
    override val description = "Create a new Xed-Editor theme"
    override val icon = Icon.ResourceIcon(drawables.palette)
    override val size: Long = 334848

    override val validConfiguration by mutableStateOf(true)
    override val projectName by derivedStateOf { Configuration.name }

    private object Configuration {
        var id by mutableStateOf("my-theme")
        var name by mutableStateOf("My Theme")
        var minAppVersion by mutableStateOf("87")
        var inheritBase by mutableStateOf(true)
    }

    @Composable
    override fun Configuration() {
        OutlinedTextField(
            value = Configuration.id,
            onValueChange = { Configuration.id = it },
            label = { Text(strings.template_id.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = Configuration.name,
            onValueChange = { Configuration.name = it },
            label = { Text(strings.name.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = Configuration.minAppVersion,
            onValueChange = { Configuration.minAppVersion = it },
            label = { Text(strings.template_min_app_version.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { Configuration.inheritBase = !Configuration.inheritBase }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = Configuration.inheritBase,
                onCheckedChange = null,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = strings.template_inherit_base_theme.getString(), style = MaterialTheme.typography.bodyLarge)
        }
    }

    override suspend fun afterClone(projectDir: FileObject) {
        val themeFile = projectDir.getChildForName("theme.json")
        if (themeFile.exists()) {
            val content = themeFile.readText() ?: return
            val json = JSONObject(content)

            json.put("id", Configuration.id)
            json.put("name", Configuration.name)
            json.put("minAppVersion", Configuration.minAppVersion.toIntOrNull() ?: 87)
            json.put("inheritBase", Configuration.inheritBase)

            themeFile.writeText(json.toString(2))
        }
    }
}
