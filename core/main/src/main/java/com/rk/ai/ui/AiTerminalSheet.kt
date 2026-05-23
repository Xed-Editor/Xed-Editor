package com.rk.ai.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.activities.main.MainViewModel
import com.rk.ai.IdeBridge
import com.rk.ai.session.AiSessionManager
import com.rk.ai.session.AiSessionManager.ConnectionStatus
import com.rk.file.FileWrapper
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.tabs.editor.EditorTab
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTerminalSheet(
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionState by AiSessionManager.state.collectAsState()
    val session = sessionState.session
    val isRunning = session?.isRunning == true
    val connectionStatus = sessionState.connectionStatus

    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    fun currentProjectDir(): String {
        val activeTab = viewModel.currentTab as? EditorTab
        activeTab?.projectRoot?.getAbsolutePath()?.takeIf { it.isNotBlank() && it.startsWith("/") }?.let { return it }
        val projectDir = ((currentDrawerTab as? FileTreeTab)?.root as? FileWrapper)?.getAbsolutePath()
            ?: drawerTabs.filterIsInstance<FileTreeTab>().mapNotNull { it.root as? FileWrapper }.firstOrNull()?.getAbsolutePath()
        if (projectDir != null && projectDir.isNotBlank()) return projectDir
        val path = activeTab?.file?.getAbsolutePath()?.takeIf { it.startsWith("/") } ?: return "/storage/emulated/0"
        val localFile = File(path)
        if (localFile.isDirectory) return localFile.absolutePath
        return localFile.parent?.takeIf { it.isNotBlank() } ?: "/storage/emulated/0"
    }

    fun startSession(extraArgs: List<String> = emptyList()) {
        val activity = context as? Activity ?: return
        val dir = currentProjectDir()
        AiSessionManager.stopSession()
        AiSessionManager.updateCwd(dir)
        scope.launch {
            withContext(Dispatchers.IO) { IdeBridge.ensureStarted(viewModel) }
            AiSessionManager.startSession(activity, viewModel, extraArgs = extraArgs)
        }
    }

    LaunchedEffect(Unit) {
        if (connectionStatus == ConnectionStatus.Disconnected) {
            startSession()
        }
    }

    LaunchedEffect(session) {
        while (isActive) {
            val text = session?.emulator?.screen?.getTranscriptText()?.toString() ?: ""
            if (text != output) output = text
            delay(100)
        }
    }

    LaunchedEffect(output) {
        if (output.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val agentList = remember { AiSessionManager.availableAgents() }
    var showAgentMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when {
                                    connectionStatus == ConnectionStatus.Error -> Color(0xFFFFC107)
                                    isRunning -> Color(0xFF4CAF50)
                                    else -> Color(0xFFEF5350)
                                },
                                RoundedCornerShape(4.dp),
                            )
                    )
                    Spacer(Modifier.width(8.dp))

                    Box {
                        TextButton(onClick = { showAgentMenu = !showAgentMenu }) {
                            Text(sessionState.currentAgent.displayName, style = MaterialTheme.typography.titleSmall)
                            Icon(
                                if (showAgentMenu) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(expanded = showAgentMenu, onDismissRequest = { showAgentMenu = false }) {
                            agentList.forEach { agent ->
                                DropdownMenuItem(
                                    text = { Text(agent.displayName) },
                                    onClick = {
                                        showAgentMenu = false
                                        if (agent != sessionState.currentAgent) {
                                            AiSessionManager.switchAgent(agent.name)
                                            startSession()
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (connectionStatus == ConnectionStatus.Connecting) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }

                    Spacer(Modifier.weight(1f))

                    if (isRunning) {
                        IconButton(onClick = { session?.write("\u0003") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Stop, "Stop", modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(onClick = { startSession() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Refresh, "Restart", modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E)),
            ) {
                when {
                    connectionStatus == ConnectionStatus.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Connection Error", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                sessionState.lastError ?: "Unknown error",
                                color = Color(0xFFAAAAAA),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { startSession() }) {
                                Text("Retry")
                            }
                        }
                    }
                    connectionStatus == ConnectionStatus.Connecting -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(color = Color(0xFF4CAF50))
                            Spacer(Modifier.height(16.dp))
                            Text("Starting ${sessionState.currentAgent.displayName}...", color = Color(0xFF888888))
                        }
                    }
                    output.isNotEmpty() -> {
                        Text(
                            text = output,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFD4D4D4),
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("AI Terminal", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF888888))
                            Spacer(Modifier.height(8.dp))
                            Text("Type a message to start", color = Color(0xFF666666))
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank()) {
                                val text = input.trim()
                                input = ""
                                if (!isRunning) {
                                    startSession(extraArgs = listOf("-p", text))
                                } else {
                                    scope.launch(Dispatchers.IO) { session?.write("$text\r") }
                                }
                            }
                        }),
                        enabled = connectionStatus != ConnectionStatus.Connecting,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                val text = input.trim()
                                input = ""
                                if (!isRunning) {
                                    startSession(extraArgs = listOf("-p", text))
                                } else {
                                    scope.launch(Dispatchers.IO) { session?.write("$text\r") }
                                }
                            }
                        },
                        enabled = input.isNotBlank() && connectionStatus != ConnectionStatus.Connecting,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "Send")
                    }
                }
            }
        }
    }
}
