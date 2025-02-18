package com.rk.runner.runners.c

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file_wrapper.FileObject
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.karbon_exec.runBashScript
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runner.RunnerImpl
import com.rk.settings.Settings
import java.io.File

class C_Runner(val file:File) : RunnerImpl() {

    override fun run(context: Context) {
        val cc = localBinDir().child("cc")
        if (cc.exists().not()) {
            cc.writeText(context.assets.open("terminal/cc.sh").bufferedReader()
                .use { it.readText() })
        }

        val runtime = Settings.terminal_runtime
        when(runtime){
            "Alpine","Android" -> {
                launchInternalTerminal(
                    context = context, TerminalCommand(
                        shell = "/bin/sh",
                        args = arrayOf(cc.absolutePath,file.absolutePath),
                        id = "cc",
                        workingDir = file.parentFile!!.absolutePath
                    )
                )
            }
            "Termux" -> {
                runBashScript(
                    context,
                    workingDir = file.parentFile!!.absolutePath,
                    script = """
# Required packages for Termux
required_packages="clang make cmake"
missing_packages=""

# Check for missing packages
for pkg in ${'$'}required_packages; do
    if ! dpkg -s "${'$'}pkg" >/dev/null 2>&1; then
        missing_packages="${'$'}missing_packages ${'$'}pkg"
    fi
done

# Install missing packages
if [ -n "${'$'}missing_packages" ]; then
    echo -e "\e[34;1m[*]\e[37m Installing missing packages: ${'$'}missing_packages\e[0m"
    pkg install -y ${'$'}missing_packages
fi

TARGET_FILE=${'$'}1

if [ -z "${'$'}TARGET_FILE" ]; then
    echo "Usage: ${'$'}0 <file.c> or a project directory"
    exit 1
fi

# Check if the target is a file
if [ -f "${'$'}TARGET_FILE" ]; then
    if echo "${'$'}TARGET_FILE" | grep -qE '\.c${'$'}'; then
        OUTPUT_FILE="${'$'}{TARGET_FILE % . c}"
        echo -e "\e[34;1m[*]\e[37m Compiling C file: ${'$'}TARGET_FILE\e[0m"
        if clang -o "${'$'}OUTPUT_FILE" "${'$'}TARGET_FILE"; then
            echo -e "\e[32;1m[✓]\e[37m Compilation successful! Running...\e[0m"
            "./${'$'}OUTPUT_FILE"
        else
            echo -e "\e[31;1m[✗]\e[37m Compilation failed!\e[0m"
            exit 1
        fi
    else
        echo "Unknown file type. Provide a .c file."
        exit 1
    fi

# Check if the target is a directory (Make or CMake project)
elif [ -d "${'$'}TARGET_FILE" ]; then
    if [ -f "${'$'}TARGET_FILE/Makefile" ]; then
        echo -e "\e[34;1m[*]\e[37m Building with Makefile\e[0m"
        if make -C "${'$'}TARGET_FILE"; then
            EXECUTABLE=${'$'}(find "${'$'}TARGET_FILE" -maxdepth 1 -type f -executable | head -n 1)
            if [ -n "${'$'}EXECUTABLE" ]; then
                echo -e "\e[32;1m[✓]\e[37m Build successful! Running ${'$'}EXECUTABLE...\e[0m"
                "${'$'}EXECUTABLE"
            else
                echo -e "\e[31;1m[✗]\e[37m No executable found after make.\e[0m"
            fi
        else
            echo -e "\e[31;1m[✗]\e[37m Build failed!\e[0m"
            exit 1
        fi
    elif [ -f "${'$'}TARGET_FILE/CMakeLists.txt" ]; then
        echo -e "\e[34;1m[*]\e[37m Building with CMake\e[0m"
        mkdir -p "${'$'}TARGET_FILE/build"
        cd "${'$'}TARGET_FILE/build"
        if cmake .. && make; then
            EXECUTABLE=${'$'}(find . -maxdepth 1 -type f -executable | head -n 1)
            if [ -n "${'$'}EXECUTABLE" ]; then
                echo -e "\e[32;1m[✓]\e[37m Build successful! Running ${'$'}EXECUTABLE...\e[0m"
                "${'$'}EXECUTABLE"
            else
                echo -e "\e[31;1m[✗]\e[37m No executable found after cmake build.\e[0m"
            fi
        else
            echo -e "\e[31;1m[✗]\e[37m Build failed!\e[0m"
            exit 1
        fi
    else
        echo "No Makefile or CMakeLists.txt found in ${'$'}TARGET_FILE"
        exit 1
    fi
else
    echo "File or directory not found: ${'$'}TARGET_FILE"
    exit 1
fi


    echo -e "\n\nProcess completed. Press Enter to go back to Xed-Editor."
    read
""".trimIndent()
                )
            }
        }
    }

    override fun getName(): String {
        return "C Compiler"
    }

    override fun getDescription(): String {
        return "Compile and run c file"
    }

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_c.getDrawable()
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {

    }
}