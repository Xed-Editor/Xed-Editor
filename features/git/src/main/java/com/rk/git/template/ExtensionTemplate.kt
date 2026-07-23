package com.rk.git.template

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.application
import com.rk.utils.logError
import org.json.JSONObject

class ExtensionTemplate : GitTemplate(repoUrl = "https://github.com/Xed-Editor/Extension-Template") {

    override val id = "xed_extension"
    override val label = "Extension"
    override val description = "Create a new Xed-Editor extension"
    override val icon = Icon.ResourceIcon(drawables.extension)
    override val size: Long = 30797312

    override val validConfiguration by mutableStateOf(true)
    override val projectName by derivedStateOf { Configuration.name }
    override val overrideRemote by derivedStateOf { Configuration.repository.takeIf { it != repoUrl } }

    private object Configuration {
        var id by mutableStateOf("com.rk.demo")
        var name by mutableStateOf("My Extension")
        var description by mutableStateOf("A demo extension template project")
        var minAppVersion by mutableStateOf("95")
        var repository by mutableStateOf("https://github.com/Xed-Editor/Extension-Template")

        var authorDisplayName by mutableStateOf("Unknown")
        var authorGithub by mutableStateOf("")
    }

    @Composable
    override fun Configuration() {
        OutlinedTextField(
            value = Configuration.id,
            onValueChange = {
                Configuration.id = it
            },
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
            value = Configuration.description,
            onValueChange = { Configuration.description = it },
            label = { Text(strings.description.getString()) },
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

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = Configuration.authorDisplayName,
                onValueChange = { Configuration.authorDisplayName = it },
                label = { Text("Display name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = Configuration.authorGithub,
                onValueChange = { Configuration.authorGithub = it },
                label = { Text("GitHub username", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1.25f),
                leadingIcon = {
                    AuthorIcon(
                        Configuration.authorGithub,
                        Modifier.size(24.dp).offset(x = 4.dp),
                    )
                },
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = Configuration.repository,
            onValueChange = { Configuration.repository = it },
            label = { Text(strings.repository.getString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    override suspend fun afterClone(projectDir: FileObject) {
        // Update /manifest.json
        val manifestFile = projectDir.getChildForName("manifest.json")
        if (manifestFile.exists()) {
            val content = manifestFile.readText() ?: return
            val json = JSONObject(content)

            json.put("id", Configuration.id)
            json.put("name", Configuration.name)
            json.put("description", Configuration.description)
            json.put("repository", Configuration.repository)
            json.put("mainClass", "${Configuration.id}.Main")
            json.put("minAppVersion", Configuration.minAppVersion.toIntOrNull() ?: 95)

            val author = JSONObject()
            author.put("displayName", Configuration.authorDisplayName)
            if (Configuration.authorGithub.isNotBlank()) {
                author.put("github", Configuration.authorGithub)
            }
            json.put("author", author)

            manifestFile.writeText(json.toString(2))
        }

        // Update /app/build.gradle.kts
        val buildFile = projectDir.getChildForName("app/build.gradle.kts")
        if (buildFile.exists()) {
            val content = buildFile.readText() ?: return

            val updatedContent =
                content
                    .replace(
                        Regex("""namespace\s*=\s*"[^"]+""""),
                        """namespace = "${Configuration.id}"""",
                    )
                    .replace(
                        Regex("""applicationId\s*=\s*"[^"]+""""),
                        """applicationId = "${Configuration.id}"""",
                    )

            buildFile.writeText(updatedContent)
        }

        // Update /settings.gradle.kts
        val settingsFile = projectDir.getChildForName("settings.gradle.kts")
        if (settingsFile.exists()) {
            val content = settingsFile.readText() ?: return

            val updatedContent =
                content.replace(
                    Regex("""rootProject.name\s*=\s*"[^"]+""""),
                    """rootProject.name = "${Configuration.name}"""",
                )

            settingsFile.writeText(updatedContent)
        }

        // Update /app/src/main/java/com/rk/demo/Main.kt
        val oldDir = projectDir.getChildForName("app/src/main/java/com/rk/demo")
        val mainFile = oldDir.getChildForName("Main.kt")
        if (!mainFile.exists()) return

        val content = mainFile.readText() ?: return

        val updatedContent =
            content.replace(
                "package com.rk.demo",
                "package ${Configuration.id}",
            )

        mainFile.writeText(updatedContent)

        // Update folder structure
        if (Configuration.id != "com.rk.demo") {
            runCatching {
                val newPackagePath = "app/src/main/java/${Configuration.id.replace(".", "/")}"
                val newDir = projectDir.getChildForName(newPackagePath)

                newDir.mkdirs()

                FileOperations.moveFile(application!!, mainFile, newDir)

                mainFile.delete()

                var dir = oldDir
                val javaDir = projectDir.getChildForName("app/src/main/java")
                while (dir.exists() && dir != javaDir) {
                    if (dir.listFiles().isNotEmpty()) break

                    val parent = dir.getParentFile() ?: break
                    dir.delete()
                    dir = parent
                }
            }
                .onFailure {
                    logError(it)
                }
        }
    }
}

@Composable
private fun AuthorIcon(githubUsername: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data("https://github.com/$githubUsername.png")
                .fallback(drawables.person)
                .placeholder(drawables.person)
                .error(drawables.person)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
    )
}
