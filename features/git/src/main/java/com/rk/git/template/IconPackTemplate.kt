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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

class IconPackTemplate : GitTemplate(repoUrl = "https://github.com/Xed-Editor/Icon-Template") {

    override val id = "xed_icon_pack"
    override val label = "Icon pack"
    override val description = "Create a new Xed-Editor icon pack"
    override val icon = Icon.ResourceIcon(drawables.widgets)
    override val size: Long = 18432

    override val validConfiguration by mutableStateOf(true)
    override var settings = emptyMap<String, Any>()

    @Composable
    override fun Configuration() {
        var id by remember { mutableStateOf("my-icons") }
        var name by remember { mutableStateOf("My Icon Pack") }
        var minAppVersion by remember { mutableStateOf("87") }
        var applyTint by remember { mutableStateOf(false) }

        SideEffect {
            settings =
                mapOf(
                    "id" to id,
                    "name" to name,
                    "minAppVersion" to minAppVersion,
                    "applyTint" to applyTint,
                )
        }

        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            label = { Text(strings.template_id.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(strings.name.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = minAppVersion,
            onValueChange = { minAppVersion = it },
            label = { Text(strings.template_min_app_version.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { applyTint = !applyTint }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = applyTint,
                onCheckedChange = null,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = strings.template_apply_tint.getString(), style = MaterialTheme.typography.bodyLarge)
        }
    }

    override suspend fun afterClone(projectDir: FileObject) {
        val manifestFile = projectDir.getChildForName("manifest.json")
        if (manifestFile.exists()) {
            val content = manifestFile.readText() ?: return
            val json = JSONObject(content)
            json.put("id", settings["id"])
            json.put("name", settings["name"])
            json.put("minAppVersion", (settings["minAppVersion"] as String).toIntOrNull() ?: 87)
            json.put("applyTint", settings["applyTint"])
            manifestFile.writeText(json.toString(4))
        }
    }
}
