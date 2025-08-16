package com.rk.xededitor.ui.screens.terminal

import android.content.Context
import android.util.Log
import com.rk.libcommons.isMainThread
import com.rk.App.Companion.getTempDir
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.terminal.getDefaultBindings
import com.rk.terminal.newSandbox
import com.rk.terminal.readStderr
import com.rk.terminal.readStdout
import java.io.File
import java.lang.Runtime.getRuntime
import kotlin.invoke

suspend fun setupRootfs(context: Context, onComplete: () -> Unit){
    if (isMainThread()) {
        throw RuntimeException("IO operation on the main thread")
    }

    val sandboxFile = File(getTempDir(), "sandbox.tar.gz")
    val rootfsFiles = sandboxDir().listFiles()?.filter {
        it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
            "tmp"
        ).absolutePath
    } ?: emptyList()


    if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
        onComplete.invoke()
    } else {

        val excludes = mutableListOf<String>()
        getDefaultBindings().forEach{
            excludes.add("--exclude")
            excludes.add(it.outside)
        }

        val process = newSandbox(excludeMounts = listOf(), root = sandboxDir(context), workingDir = "/","tar",*(excludes.toTypedArray()),"-xf", sandboxFile.absolutePath,).apply {
            println(readStdout())
        }

        process.waitFor()
        sandboxFile.delete()

        with(sandboxDir()) {
            child("etc/hostname").writeText("Xed-Editor")
            child("etc/resolv.conf").also { it.createFileIfNot(); it.writeText(nameserver) }
            child("etc/hosts").writeText(hosts)
        }
        onComplete.invoke()
    }
}
