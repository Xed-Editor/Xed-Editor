package com.rk.terminal

import com.rk.file.sandboxDir
import com.rk.utils.application
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localBinDir
import com.rk.file.localDir

fun setupTerminalFiles() {
    if (sandboxDir().exists().not() || localBinDir().exists().not()) return

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

    val internalFiles = listOf("init", "sandbox", "setup", "utils")
    internalFiles.forEach { setupAssetFile(it) }

    val lspFiles = listOf("python", "html", "css", "typescript", "json", "bash")
    lspFiles.forEach { setupLspFile(it) }
}

fun setupLspFile(fileName: String) = setupAssetFile("lsp/$fileName")

fun setupAssetFile(fileName: String) {
    with(localBinDir().child(fileName)) {
        parentFile?.mkdir()
        if (exists().not()) {
            createFileIfNot()
            writeText(
                application!!.assets
                    .open("terminal/$fileName.sh")
                    .bufferedReader()
                    .use { it.readText() }
            )
        }
    }
}
