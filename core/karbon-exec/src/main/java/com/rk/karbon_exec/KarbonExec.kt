package com.rk.karbon_exec

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rk.libcommons.application

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
        throw RuntimeException("Tried to use KarbonExec functions but termux is not installed")
    }
}

fun isTermuxCompatible():Boolean{
    checkTermuxInstall()
    val intent = Intent("com.termux.RUN_COMMAND").apply {
        setPackage(TERMUX_PKG)
    }
    val activities = application!!.packageManager.queryIntentServices(intent,0)
    return activities.isNotEmpty()
}

fun testExecPermission():Pair<Boolean,Exception?>{
    checkTermuxInstall()
    try {
        runCommandTermux(application!!,"$TERMUX_PREFIX/bin/echo", arrayOf(), background = true)
        return Pair(true,null)
    }catch (e:Exception){
        return Pair(false,e)
    }
}


fun runCommandTermux(context: Context, exe: String, args: Array<String>, background: Boolean = true,cwd:String? = null) {
    val intent = Intent("com.termux.RUN_COMMAND").apply {
        setClassName(TERMUX_PKG, "com.termux.app.RunCommandService")
        putExtra("com.termux.RUN_COMMAND_PATH", exe)
        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
        putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        cwd?.let { cwd ->
            putExtra("$TERMUX_PKG.RUN_COMMAND_SERVICE.EXTRA_WORKDIR",cwd)
        }
    }
    context.startForegroundService(intent)
}