package com.rk.xededitor.ui.screens.terminal

import com.rk.file.sandboxDir
import com.rk.libcommons.application
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localBinDir
import com.rk.file.localDir

fun setupTerminalFiles() {
    if (sandboxDir().exists().not()) {
        return
    }

    if (localBinDir().exists().not()) {
        return
    }

    with(localBinDir().child("init")) {
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/init.sh").bufferedReader()
                    .use { it.readText() }
            )
        }
    }

    with(localBinDir().child("sandbox")) {
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/sandbox.sh").bufferedReader()
                    .use { it.readText() }
            )
        }
    }

    with(localBinDir().child("setup")) {
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/setup.sh").bufferedReader()
                    .use { it.readText() }
            )
        }
    }

    with(localDir().child("stat")) {
        if (exists().not()) {
            createFileIfNot()
            writeText(stat)
        }
    }

    with(localDir().child("vmstat")) {
        if (exists().not()) {
            createFileIfNot()
            writeText(vmstat)
        }
    }

    setupLspFile("python")
    setupLspFile("html")
    setupLspFile("css")
    setupLspFile("typescript")
    setupLspFile("json")
    setupLspFile("bash")
}

fun setupLspFile(scriptName: String) {
    with(localBinDir().child("lsp/$scriptName")) {
        parentFile?.mkdir()
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets.open("terminal/lsp/$scriptName.sh").bufferedReader()
                    .use { it.readText() }
            )
        }
    }
}