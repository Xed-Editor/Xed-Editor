package com.rk.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.exec.isTerminalInstalled
import com.rk.resources.strings
import java.io.File

/**
 * Dialog that gathers everything needed to scaffold a project and emits a [ProjectConfig].
 *
 * All projects are created inside the terminal sandbox home (exec-capable), so the toolchain
 * (gradle/npm/python) can actually run from where the project lives. There is no longer a
 * Documents/XED option: Android shared storage is mounted noexec and ignores Unix permissions, so
 * nothing buildable could run there anyway.
 *
 * @param projectsDir the terminal sandbox home in which the project folder is created.
 * @param onCreate invoked with the validated config when the user taps Create.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(projectsDir: File, onDismiss: () -> Unit, onCreate: (ProjectConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ProjectTemplate.NONE) }
    var modLoader by remember { mutableStateOf(ModLoader.FABRIC) }
    var packageName by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var modId by remember { mutableStateOf("") }
    var modDescription by remember { mutableStateOf("") }
    var modVersion by remember { mutableStateOf("1.0.0") }
    var minecraftVersion by remember { mutableStateOf("") }
    var jdkVersion by remember { mutableStateOf("21") }
    var initGit by remember { mutableStateOf(false) }

    // Minecraft versions: show fallback immediately, then replace with the live Mojang list.
    var mcVersions by remember { mutableStateOf(MinecraftVersions.FALLBACK) }
    var loadingVersions by remember { mutableStateOf(false) }
    LaunchedEffect(template) {
        if (template == ProjectTemplate.MINECRAFT_MOD) {
            if (minecraftVersion.isBlank()) minecraftVersion = mcVersions.first()
            loadingVersions = true
            mcVersions = MinecraftVersions.fetchReleases()
            if (minecraftVersion !in mcVersions) minecraftVersion = mcVersions.first()
            loadingVersions = false
        }
    }

    val parentDir = projectsDir
    val trimmedName = name.trim()

    // Live detection of the toolchain the chosen template needs (Python/Node/JDK).
    var toolState by remember { mutableStateOf<ToolState>(ToolState.None) }
    LaunchedEffect(template, jdkVersion) {
        val cfg = ProjectConfig(name = "check", template = template, parentDir = parentDir, jdkVersion = jdkVersion)
        val tools = ProjectDependencies.requiredTools(cfg)
        when {
            tools.isEmpty() -> toolState = ToolState.None
            !isTerminalInstalled() -> toolState = ToolState.NoTerminal
            else -> {
                toolState = ToolState.Checking
                val missing = ProjectDependencies.missingTools(tools)
                toolState =
                    if (missing.isEmpty()) ToolState.Ready else ToolState.Missing(missing.map { it.name })
            }
        }
    }

    val nameError: String? =
        when {
            trimmedName.isEmpty() -> null
            trimmedName.contains('/') || trimmedName.contains('\\') || trimmedName == "." || trimmedName == ".." ->
                stringResource(strings.invalid_project_name_err)
            File(parentDir, trimmedName).exists() -> stringResource(strings.project_exists_err)
            else -> null
        }

    val canCreate = trimmedName.isNotEmpty() && nameError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.create_project)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(strings.project_name)) },
                    isError = nameError != null,
                    supportingText =
                        nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                LabeledDropdown(
                    label = stringResource(strings.project_template),
                    selected = template.displayName,
                    options = ProjectTemplate.entries.map { it.displayName },
                    onSelect = { display -> template = ProjectTemplate.entries.first { it.displayName == display } },
                )

                // Minecraft-specific options
                if (template.showsMinecraftOptions) {
                    LabeledDropdown(
                        label = stringResource(strings.mod_loader),
                        selected = modLoader.displayName,
                        options = ModLoader.entries.map { it.displayName },
                        onSelect = { display -> modLoader = ModLoader.entries.first { it.displayName == display } },
                    )
                    LabeledDropdown(
                        label =
                            stringResource(strings.minecraft_version) +
                                if (loadingVersions) " (${stringResource(strings.loading_versions)})" else "",
                        selected = minecraftVersion.ifBlank { mcVersions.first() },
                        options = mcVersions,
                        onSelect = { minecraftVersion = it },
                    )
                    OutlinedTextField(
                        value = modId,
                        onValueChange = { modId = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.mod_id)) },
                        placeholder = { Text(trimmedName.lowercase().replace(Regex("[^a-z0-9_]"), "_")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modDescription,
                        onValueChange = { modDescription = it },
                        label = { Text(stringResource(strings.mod_description)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modVersion,
                        onValueChange = { modVersion = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.mod_version)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Package name (Minecraft + Android)
                if (template.showsPackageName) {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.package_name)) },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.author_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LabeledDropdown(
                        label = stringResource(strings.jdk_version),
                        selected = jdkVersion,
                        options = listOf("8", "11", "17", "21"),
                        onSelect = { jdkVersion = it },
                    )
                }

                // Initialize Git repository
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(strings.git_init), modifier = Modifier.weight(1f))
                    Switch(checked = initGit, onCheckedChange = { initGit = it })
                }

                // Live toolchain status for the selected template
                when (val state = toolState) {
                    ToolState.None -> {}
                    ToolState.Checking ->
                        ToolStatusRow(stringResource(strings.tools_checking), MaterialTheme.colorScheme.onSurfaceVariant)
                    ToolState.Ready ->
                        ToolStatusRow(stringResource(strings.tools_installed), MaterialTheme.colorScheme.primary)
                    ToolState.NoTerminal ->
                        ToolStatusRow(stringResource(strings.tools_no_terminal), MaterialTheme.colorScheme.error)
                    is ToolState.Missing ->
                        ToolStatusRow(
                            stringResource(strings.tools_missing) + " " + state.names.joinToString(", "),
                            MaterialTheme.colorScheme.tertiary,
                        )
                }

                // Resolved location
                Text(
                    text = stringResource(strings.project_location) + ": " +
                        File(parentDir, trimmedName.ifBlank { "<name>" }).absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    onCreate(
                        ProjectConfig(
                            name = trimmedName,
                            template = template,
                            parentDir = parentDir,
                            packageName = packageName.trim(),
                            author = author.trim(),
                            modLoader = if (template.showsMinecraftOptions) modLoader else null,
                            modId = modId.trim(),
                            modDescription = modDescription.trim(),
                            modVersion = modVersion.trim().ifBlank { "1.0.0" },
                            minecraftVersion = minecraftVersion.trim(),
                            jdkVersion = jdkVersion.trim().ifBlank { "21" },
                            initGit = initGit,
                        )
                    )
                },
            ) {
                Text(stringResource(strings.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolStatusRow(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** UI state for the live toolchain check shown in the create dialog. */
private sealed interface ToolState {
    data object None : ToolState

    data object Checking : ToolState

    data object Ready : ToolState

    data object NoTerminal : ToolState

    data class Missing(val names: List<String>) : ToolState
}

