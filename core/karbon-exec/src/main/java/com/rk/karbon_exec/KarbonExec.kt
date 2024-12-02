package com.rk.karbon_exec

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


const val TERMUX_PKG="com.termux"

@SuppressLint("SdCardPath")
const val TERMUX_PREFIX="/data/data/$TERMUX_PKG/files/usr"


fun isTermuxInstalled():Boolean{
    val packageManager: PackageManager = application!!.packageManager
    val intent = packageManager.getLaunchIntentForPackage(TERMUX_PKG) ?: return false
    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    return list.size > 0
}

private fun checkTermuxInstall(){
    if (isTermuxInstalled().not()){
        throw RuntimeException("Termux is not installed")
    }
    if (isTermuxCompatible().not()){
        throw RuntimeException("Termux is not compatible")
    }
    if (isExecPermissionGranted().not()){
        throw RuntimeException("Termux-Exec Permission Denied")
    }
}

fun isTermuxCompatible():Boolean{
    val intent = Intent("$TERMUX_PKG.RUN_COMMAND").apply {
        setPackage(TERMUX_PKG)
    }
    val activities = application!!.packageManager.queryIntentServices(intent,0)
    return activities.isNotEmpty()
}

fun testExecPermission():Pair<Boolean,Exception?>{
    try {
        checkTermuxInstall()
        runCommandTermux(application!!,"$TERMUX_PREFIX/bin/echo", arrayOf(), background = true)
        return Pair(true,null)
    }catch (e:Exception){
        return Pair(false,e)
    }
}

fun isTermuxRunning(): Boolean {
    val activityManager = application!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningProcesses = activityManager.runningAppProcesses
    return runningProcesses.any { it.processName == TERMUX_PKG }
}

fun isExecPermissionGranted():Boolean{
    return ContextCompat.checkSelfPermission(application!!, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED
}

fun runCommandTermux(context: Context, exe: String, args: Array<String>, background: Boolean = true,cwd:String? = null):Boolean {
    checkTermuxInstall()
    val intent = Intent("$TERMUX_PKG.RUN_COMMAND").apply {
        setClassName(TERMUX_PKG, "$TERMUX_PKG.app.RunCommandService")
        putExtra("$TERMUX_PKG.RUN_COMMAND_PATH", exe)
        putExtra("$TERMUX_PKG.RUN_COMMAND_ARGUMENTS", args)
        putExtra("$TERMUX_PKG.RUN_COMMAND_BACKGROUND", background)
        cwd?.let { cwd ->
            putExtra("$TERMUX_PKG.RUN_COMMAND_SERVICE.EXTRA_WORKDIR",cwd)
        }
    }
    context.startForegroundService(intent)
    return isTermuxRunning()
}

fun launchTermux():Boolean{
    if (isTermuxInstalled().not()){
        return false
    }
    application!!.startActivity(application!!.packageManager.getLaunchIntentForPackage(TERMUX_PKG))
    return true
}

fun askLaunchTermux(context: Activity,scope:CoroutineScope,onLaunch:()->Unit,onCancel:()->Unit){
    if (isTermuxInstalled().not()){
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context,"Termux not installed",Toast.LENGTH_SHORT).show()
        }
        return
    }
    if (isTermuxRunning()){return}
    scope.launch(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context).apply {
            setTitle("Launch Termux?")
            setMessage("Termux is force stopped so karbon is unable to run command in it. do you like to start it?")
            setPositiveButton("Launch",{dialog,which ->
                launchTermux()
                application!!.startActivity(Intent(application!!,context::class.java))
                onLaunch.invoke()})
            setNegativeButton("Cancel",{dialog,which -> onCancel.invoke()})
            show()
        }
    }
}

