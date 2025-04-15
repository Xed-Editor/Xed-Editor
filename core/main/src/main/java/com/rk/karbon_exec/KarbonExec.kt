package com.rk.karbon_exec

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.application
import com.rk.libcommons.pendingCommand
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TERMUX_PKG = "com.termux"

@SuppressLint("SdCardPath")
const val TERMUX_PREFIX = "/data/data/$TERMUX_PKG/files/usr"

fun isTermuxInstalled(): Boolean {
    val packageManager: PackageManager = application!!.packageManager
    val intent = packageManager.getLaunchIntentForPackage(TERMUX_PKG) ?: return false
    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    return list.size > 0
}

private fun checkTermuxInstall() {
    if (isTermuxInstalled().not()) {
        throw RuntimeException(strings.err_no_termux.getString())
    }
    if (isTermuxCompatible().not()) {
        throw RuntimeException(strings.err_termux_incompatible.getString())
    }
    if (isExecPermissionGranted().not()) {
        throw RuntimeException("${strings.termux_exec.getString()} ${strings.permission_denied.getString()}")
    }
}

fun isTermuxCompatible(): Boolean {
    val intent = Intent("$TERMUX_PKG.RUN_COMMAND").apply {
        setPackage(TERMUX_PKG)
    }
    val activities = application!!.packageManager.queryIntentServices(intent, 0)
    return activities.isNotEmpty()
}

fun testExecPermission(): Pair<Boolean, Exception?> {
    try {
        checkTermuxInstall()
        runCommandTermux(application!!, "$TERMUX_PREFIX/bin/echo", arrayOf(), background = true, isTesting = true)
        return Pair(true, null)
    } catch (e: Exception) {
        return Pair(false, e)
    }
}


fun isExecPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(
        application!!, "com.termux.permission.RUN_COMMAND"
    ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(DelicateCoroutinesApi::class)
fun runCommandTermux(
    context: Context,
    exe: String,
    args: Array<String>,
    background: Boolean = true,
    cwd: String? = null,
    isTesting: Boolean = false
) {
    runCatching { checkTermuxInstall() }.onFailure { error(it) }.onSuccess {
        GlobalScope.launch(Dispatchers.Main) {
            if (isTesting.not()) {
                runCatching { launchTermux() }
                delay(200)
            }
            val intent = Intent("$TERMUX_PKG.RUN_COMMAND").apply {
                setClassName(TERMUX_PKG, "$TERMUX_PKG.app.RunCommandService")
                putExtra("$TERMUX_PKG.RUN_COMMAND_PATH", exe)
                putExtra("$TERMUX_PKG.RUN_COMMAND_ARGUMENTS", args)
                putExtra("$TERMUX_PKG.RUN_COMMAND_BACKGROUND", background)
                cwd?.let { cwd ->
                    putExtra("$TERMUX_PKG.RUN_COMMAND_SERVICE.EXTRA_WORKDIR", cwd)
                }
            }
            context.startForegroundService(intent)
        }
    }
}


fun runBashScript(
    context: Context, script: String, workingDir: String? = null, background: Boolean = false
) {
    runCommandTermux(
        context = context,
        exe = "$TERMUX_PREFIX/bin/bash",
        arrayOf("-c", script),
        background = background,
        cwd = workingDir
    )
}

fun launchInternalTerminal(context: Context, terminalCommand: TerminalCommand) {
    pendingCommand = terminalCommand
    context.startActivity(
        Intent(
            context, Class.forName("com.rk.xededitor.ui.activities.terminal.Terminal")
        )
    )
}

fun launchTermux(): Boolean {
    if (isTermuxInstalled().not()) {
        return false
    }
    application!!.startActivity(application!!.packageManager.getLaunchIntentForPackage(TERMUX_PKG))
    return true
}
