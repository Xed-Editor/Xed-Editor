package com.rk.git.template

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import org.json.JSONObject

class ExtensionTemplate : GitTemplate(repoUrl = "https://github.com/Xed-Editor/Extension-Template") {

    override val id = "xed_extension"
    override val label = "Extension"
    override val description = "Create a new Xed-Editor extension"
    override val icon = Icon.ResourceIcon(drawables.extension)
    override val size: Long = 30797312

    override val validConfiguration by mutableStateOf(true)
    override var settings = emptyMap<String, Any>()

    @Composable
    override fun Configuration() {
        var id by remember { mutableStateOf("com.rk.demo") }
        var name by remember { mutableStateOf("My Extension") }
        var minAppVersion by remember { mutableStateOf("95") }
        var mainClass by remember { mutableStateOf("com.rk.demo.Main") }

        SideEffect {
            settings =
                mapOf(
                    "id" to id,
                    "name" to name,
                    "minAppVersion" to minAppVersion,
                    "mainClass" to mainClass,
                )
        }

        OutlinedTextField(
            value = id,
            onValueChange = {
                id = it
                mainClass = "$it.Main"
            },
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
            value = mainClass,
            onValueChange = { mainClass = it },
            label = { Text(strings.template_main_class.getString()) },
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
    }

    override suspend fun afterClone(projectDir: FileObject) {
        val manifestFile = projectDir.getChildForName("manifest.json")
        if (manifestFile.exists()) {
            val content = manifestFile.readText() ?: return
            val json = JSONObject(content)
            json.put("id", settings["id"])
            json.put("name", settings["name"])
            json.put("mainClass", settings["mainClass"])
            json.put("minAppVersion", (settings["minAppVersion"] as String).toIntOrNull() ?: 95)
            manifestFile.writeText(json.toString(4))
        }
    }
}
