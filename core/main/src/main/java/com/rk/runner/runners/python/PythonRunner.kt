package com.rk.runner.runners.python

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.launchInternalTerminal
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.resources.drawables
import com.rk.runBashScript
import com.rk.runner.RunnerImpl
import com.rk.settings.Settings
import java.io.File

class PythonRunner(val file: File, val isTermuxFile: Boolean = false) : RunnerImpl() {
    override fun run(context: Context) {
        val py = localBinDir().child("python")
        if (py.exists().not()) {
            py.writeText(context.assets.open("terminal/python.sh").bufferedReader()
                .use { it.readText() })
        }
        val runtime = if (isTermuxFile) {
            "Termux"
        } else {
            Settings.terminal_runtime
        }
        when (runtime) {
            "Alpine" -> {
                launchInternalTerminal(
                    context = context, TerminalCommand(
                        shell = "/bin/sh",
                        args = arrayOf(py.absolutePath, file.absolutePath),
                        id = "python",
                        workingDir = file.parentFile!!.absolutePath
                    )
                )
            }

            "Termux" -> {
                runBashScript(
                    context,
                    workingDir = file.parentFile!!.absolutePath,
                    script = """
    required_packages="python"
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

    python "${file.absolutePath}"
    echo -e "\n\nProcess completed. Press Enter to go back to Xed-Editor."
    read
""".trimIndent()
                )
            }
        }
    }

    override fun getName(): String {
        return "Python"
    }

    override fun getDescription(): String {
        return "Python Interpreter"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, drawables.ic_language_python)
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {

    }
}
