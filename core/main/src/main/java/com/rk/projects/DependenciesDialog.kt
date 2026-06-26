package com.rk.projects

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rk.exec.ShellUtils
import com.rk.exec.isTerminalInstalled
import com.rk.resources.strings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Detection-only state for a tool (whether it's already present). */
private enum class DepState {
    AVAILABLE,
    INSTALLED,
}

/** A downloadable tool: a display summary, a shell detection command, and a full install command. */
private data class Dep(val name: String, val summary: String, val detectCmd: String, val installCmd: String)

private class DepRow(val dep: Dep) {
    var state by mutableStateOf(DepState.AVAILABLE)
}

/**
 * Best-effort Android SDK install (command-line tools + platform-tools + android-34 + build-tools).
 * Heavy and arch-dependent on a phone — offered as an option only, per the user's choice to install.
 */
private val ANDROID_SDK_INSTALL =
    "set -e; " +
        "export ANDROID_HOME=\"${'$'}HOME/android-sdk\"; " +
        "mkdir -p \"${'$'}ANDROID_HOME/cmdline-tools\"; " +
        "apt-get update -y; apt-get install -y wget unzip openjdk-17-jdk; " +
        "cd \"${'$'}ANDROID_HOME/cmdline-tools\"; " +
        "wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O clt.zip; " +
        "unzip -q -o clt.zip; rm -f clt.zip; rm -rf latest; mv cmdline-tools latest; " +
        "yes | latest/bin/sdkmanager --sdk_root=\"${'$'}ANDROID_HOME\" --licenses || true; " +
        "latest/bin/sdkmanager --sdk_root=\"${'$'}ANDROID_HOME\" \"platform-tools\" \"platforms;android-34\" \"build-tools;34.0.0\""

private fun catalogFor(type: DetectedProjectType): List<Dep> {
    fun apt(pkgs: String) = "apt-get install -y $pkgs"
    val jdk21 = Dep("JDK 21", "openjdk-21-jdk", "ls -d /usr/lib/jvm/java-21* >/dev/null 2>&1", apt("openjdk-21-jdk"))
    val jdk17 = Dep("JDK 17", "openjdk-17-jdk", "ls -d /usr/lib/jvm/java-17* >/dev/null 2>&1", apt("openjdk-17-jdk"))
    val git = Dep("Git", "git", "command -v git >/dev/null 2>&1", apt("git"))
    return when (type) {
        DetectedProjectType.FABRIC_MOD,
        DetectedProjectType.FORGE_MOD,
        DetectedProjectType.GRADLE -> listOf(jdk21, jdk17, git)
        DetectedProjectType.ANDROID ->
            listOf(
                jdk21,
                jdk17,
                git,
                Dep(
                    "Android SDK (command-line tools)",
                    "platform-tools · android-34 · build-tools 34 (large)",
                    "test -d \"${'$'}HOME/android-sdk/platform-tools\"",
                    ANDROID_SDK_INSTALL,
                ),
            )
        DetectedProjectType.NODE ->
            listOf(Dep("Node.js & npm", "nodejs npm", "command -v node >/dev/null 2>&1", apt("nodejs npm")))
        DetectedProjectType.PYTHON ->
            listOf(
                Dep(
                    "Python 3",
                    "python3 python3-pip python3-venv",
                    "command -v python3 >/dev/null 2>&1",
                    apt("python3 python3-pip python3-venv"),
                ),
                Dep("pipx", "pipx", "command -v pipx >/dev/null 2>&1", apt("pipx")),
            )
        DetectedProjectType.RUST ->
            listOf(Dep("Rust (cargo)", "rustc cargo", "command -v cargo >/dev/null 2>&1", apt("rustc cargo")))
        DetectedProjectType.GO -> listOf(Dep("Go", "golang-go", "command -v go >/dev/null 2>&1", apt("golang-go")))
        DetectedProjectType.WEB,
        DetectedProjectType.UNKNOWN -> emptyList()
    }
}

/**
 * Lists the tools a project needs, shows installed vs available, and installs the selected ones via
 * the background [DependencyInstallService] (a foreground service, so it keeps running and shows a
 * progress notification even if the user leaves the app). Notification permission is requested
 * first (Android 13+). The dialog cannot be dismissed while an install is running.
 */
@Composable
fun DependenciesDialog(projectRoot: File, onDismiss: () -> Unit) {
    val context = LocalContext.current

    var detecting by remember { mutableStateOf(true) }
    var typeLabel by remember { mutableStateOf("") }
    var terminalReady by remember { mutableStateOf(true) }

    val rows = remember { mutableStateListOf<DepRow>() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    val busy = DependencyInstaller.running

    LaunchedEffect(projectRoot.absolutePath) {
        detecting = true
        if (!DependencyInstaller.running) DependencyInstaller.status.clear()
        val type = withContext(Dispatchers.IO) { ProjectTypeDetector.detect(projectRoot) }
        typeLabel = type.label
        terminalReady = isTerminalInstalled()
        rows.clear()
        rows.addAll(catalogFor(type).map { DepRow(it) })
        if (terminalReady) {
            rows.forEach { row ->
                val installed =
                    withContext(Dispatchers.IO) {
                        ShellUtils.runUbuntu(command = arrayOf("bash", "-lc", row.dep.detectCmd), timeoutSeconds = 15L)
                            .exitCode == 0
                    }
                row.state = if (installed) DepState.INSTALLED else DepState.AVAILABLE
            }
        }
        detecting = false
    }

    val doInstall: () -> Unit = {
        val sel =
            rows.filter {
                selected[it.dep.name] == true &&
                    it.state != DepState.INSTALLED &&
                    DependencyInstaller.status[it.dep.name].let { s -> s == null || s == DepInstallStatus.FAILED }
            }
        if (sel.isNotEmpty()) {
            val names = ArrayList(sel.map { it.dep.name })
            val commands = ArrayList(sel.map { it.dep.installCmd })
            DependencyInstallService.start(context, names, commands)
        }
    }

    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { doInstall() }

    val onDownloadClicked: () -> Unit = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            doInstall()
        }
    }

    val hasSelection =
        rows.any {
            selected[it.dep.name] == true &&
                it.state != DepState.INSTALLED &&
                DependencyInstaller.status[it.dep.name].let { s -> s == null || s == DepInstallStatus.FAILED }
        }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(stringResource(strings.dependencies) + if (typeLabel.isNotBlank()) "  ·  $typeLabel" else "")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    detecting ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(stringResource(strings.detecting_project))
                        }
                    !terminalReady ->
                        Text(stringResource(strings.tools_no_terminal), color = MaterialTheme.colorScheme.error)
                    rows.isEmpty() -> Text(stringResource(strings.no_dependencies_needed))
                    else -> rows.forEach { row -> DepRowItem(row, selected, busy) }
                }

                if (busy && DependencyInstaller.currentName.isNotBlank()) {
                    Text(
                        text = stringResource(strings.installing) + "  " + DependencyInstaller.currentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !detecting && !busy && terminalReady && hasSelection,
                onClick = onDownloadClicked,
            ) {
                Text(stringResource(if (busy) strings.installing else strings.download))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(strings.close)) } },
    )
}

@Composable
private fun DepRowItem(row: DepRow, selected: MutableMap<String, Boolean>, busy: Boolean) {
    val svc = DependencyInstaller.status[row.dep.name]
    val selectable = !busy && row.state == DepState.AVAILABLE && (svc == null || svc == DepInstallStatus.FAILED)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = selected[row.dep.name] == true,
            enabled = selectable,
            onCheckedChange = { selected[row.dep.name] = it },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.dep.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = row.dep.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))

        if (svc == DepInstallStatus.INSTALLING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            val installed = row.state == DepState.INSTALLED || svc == DepInstallStatus.DONE
            val failed = svc == DepInstallStatus.FAILED
            val queued = svc == DepInstallStatus.PENDING
            Text(
                text =
                    when {
                        installed -> stringResource(strings.dep_installed)
                        failed -> stringResource(strings.dep_failed)
                        queued -> stringResource(strings.dep_queued)
                        else -> stringResource(strings.dep_available)
                    },
                style = MaterialTheme.typography.labelMedium,
                color =
                    when {
                        installed -> Color(0xFF4CAF50)
                        failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}
