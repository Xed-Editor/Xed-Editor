package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.file.BuiltinFileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig

class XML : BaseLspServer() {
    override val id: String = "xml"
    override val languageName: String = "XML"
    override val serverName = "lemminx"
    override val supportedExtensions: List<String> = BuiltinFileType.XML.extensions
    override val icon = BuiltinFileType.XML.icon

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }
        return localDir().child("org.eclipse.lemminx.uber-jar_0.31.0.jar").exists() &&
            sandboxDir().child("bin/java").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/xml")

        launchInternalTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installSH.absolutePath),
                    id = "xml-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(
            arrayOf("java", "-jar", localDir().child("org.eclipse.lemminx.uber-jar_0.31.0.jar").absolutePath)
        )
    }
}
