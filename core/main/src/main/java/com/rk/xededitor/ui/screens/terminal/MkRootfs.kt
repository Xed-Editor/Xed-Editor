package com.rk.xededitor.ui.screens.terminal

import android.content.Context
import com.rk.libcommons.sandboxDir
import com.rk.libcommons.sandboxHomeDir
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.isMainThread
import com.rk.App.Companion.getTempDir
import java.io.File
import java.lang.Runtime.getRuntime

class MkRootfs(val context: Context, private val onComplete: () -> Unit) {
    private val sandboxFile = File(getTempDir(), "sandbox.tar.gz")

    init {
        val rootfsFiles = sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
                "tmp"
            ).absolutePath
        } ?: emptyList()


        if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
            onComplete.invoke()
            println("completed")
        } else {
            initializeInternal()
        }
    }

    private fun initializeInternal() {
        if (isMainThread()) {
            throw RuntimeException("IO operation on the main thread")
        }
        getRuntime().exec("tar -xf ${sandboxFile.absolutePath} -C ${sandboxDir()}").waitFor()
        sandboxFile.delete()
        with(sandboxDir()) {
            child("etc/hostname").writeText("Xed-Editor")
            child("etc/resolv.conf").also { it.createFileIfNot();it.writeText(nameserver) }
            child("etc/hosts").writeText(hosts)
        }
        onComplete.invoke()
    }
}