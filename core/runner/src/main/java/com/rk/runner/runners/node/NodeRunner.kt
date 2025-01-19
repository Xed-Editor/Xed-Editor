package com.rk.runner.runners.node

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.karbon_exec.runBashScript
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.localBinDir
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import java.io.File

class NodeRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        val node = File(context.localBinDir(), "node")
        if (node.exists().not()) {
            node.writeText(context.assets.open("terminal/nodejs.sh").bufferedReader()
                .use { it.readText() })
        }
        val runtime = PreferencesData.getString(PreferencesKeys.TERMINAL_RUNTIME,"Alpine")
        when(runtime){
            "Alpine","Android" -> {
                launchInternalTerminal(
                    context = context, TerminalCommand(
                        shell = "/bin/sh",
                        args = arrayOf(node.absolutePath,file.absolutePath),
                        id = "node",
                        workingDir = file.parentFile.absolutePath
                    )
                )
            }
            "Termux" -> {
                runBashScript(
                    context,
                    workingDir = file.parentFile!!.absolutePath,
                    script = """
    required_packages="nodejs"
    missing_packages=""

    # Check for missing packages
    for pkg in ${'$'}required_packages; do
        if ! dpkg -l | grep -q "^ii  ${'$'}pkg"; then
            missing_packages="${'$'}missing_packages ${'$'}pkg"
        fi
    done

    # Install missing packages if any
    if [ -n "${'$'}missing_packages" ]; then
        echo -e "\e[34;1m[*]\e[37m Installing missing packages: ${'$'}missing_packages\e[0m"
        pkg install -y ${'$'}missing_packages
    fi

    node "${file.absolutePath}"
    echo -e "\n\nProcess completed. Press Enter to go back to Xed-Editor."
    read
""".trimIndent()
                )
            }
        }
    }

    override fun getName(): String {
        return "Nodejs"
    }

    override fun getDescription(): String {
        return "Nodejs"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, drawables.ic_language_js)
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}
