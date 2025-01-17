package com.rk.karbon_exec

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.application
import com.rk.libcommons.isAppInBackground
import com.rk.libcommons.pendingCommand
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
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
        throw RuntimeException("Termux is not installed")
    }
    if (isTermuxCompatible().not()) {
        throw RuntimeException("Termux is not compatible")
    }
    if (isExecPermissionGranted().not()) {
        throw RuntimeException("Termux-Exec Permission Denied")
    }
}

fun askLaunchTermux(context: Context){
    Handler(Looper.getMainLooper()).post {
        MaterialAlertDialogBuilder(context).apply {
            setTitle(strings.launch_termux.getString())
            setMessage(strings.launch_termux_explanation.getString())
            setPositiveButton(strings.launch.getString(), { dialog, which ->
                launchTermux()
                Handler(Looper.getMainLooper()).postDelayed({
                    val returnIntent = Intent(context, Class.forName("com.rk.xededitor.MainActivity.MainActivity")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(returnIntent)
                }, 300)
            })
            setNegativeButton(strings.cancel.getString(), { dialog, which -> })
            show()
        }
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
        runCommandTermux(application!!, "$TERMUX_PREFIX/bin/echo", arrayOf(), background = true)
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
    cwd: String? = null
) {
    runCatching { checkTermuxInstall() }.onFailure { toast(it.message) }.onSuccess {
        GlobalScope.launch(Dispatchers.Main) {
            runCatching { launchTermux() }
            delay(200)
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



fun runBashScript(context: Context, script: String,workingDir:String? = null, background: Boolean = false) {
     runCommandTermux(
        context = context,
        exe = "$TERMUX_PREFIX/bin/bash",
        arrayOf("-c", script),
        background = background,
         cwd = workingDir
    )
}

fun launchInternalTerminal(context: Context,terminalCommand: TerminalCommand){
    pendingCommand = terminalCommand
    context.startActivity(
        Intent(
            context,
            Class.forName("com.rk.xededitor.ui.activities.terminal.Terminal")
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
