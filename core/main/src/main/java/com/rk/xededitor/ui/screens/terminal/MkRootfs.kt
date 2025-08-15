package com.rk.xededitor.ui.screens.terminal

import android.content.Context
import com.rk.libcommons.isMainThread
import com.rk.App.Companion.getTempDir
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import java.io.File
import java.lang.Runtime.getRuntime

class MkRootfs(val context: Context, private val onComplete: (String?) -> Unit) {
    private val sandboxFile = File(getTempDir(), "sandbox.tar.gz")

    init {
        val rootfsFiles = sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
                "tmp"
            ).absolutePath
        } ?: emptyList()


        if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
            onComplete.invoke(null)
        } else {
            initializeInternal()
        }
    }

    private fun initializeInternal() {
        if (isMainThread()) {
            throw RuntimeException("IO operation on the main thread")
        }
        val process = ProcessBuilder(
            "tar",
            "--no-same-owner",
            "--exclude", "/var/lock",
            "--exclude", "/dev",
            "--exclude", "/proc",
            "--exclude", "/etc/alternatives",
            "--exclude", "/etc/systemd",
            "-xf", sandboxFile.absolutePath,
            "-C", sandboxDir().absolutePath
        ).start()

        val result = process.waitFor()
        sandboxFile.delete()

        if (result == 0) {
            with(sandboxDir()) {
                child("etc/hostname").writeText("Xed-Editor")
                child("etc/resolv.conf").also { it.createFileIfNot(); it.writeText(nameserver) }
                child("etc/hosts").writeText(hosts)
            }
            onComplete.invoke(null)
        } else {
            val error = process.errorStream.bufferedReader().use { it.readText() }
            onComplete.invoke(error)
        }



    }
}