package com.rk.runner.runners.c

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.launchInternalTerminal
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runBashScript
import com.rk.runner.RunnerImpl
import com.rk.settings.Settings
import java.io.File

class C_Runner(val file: File, val isTermuxFile: Boolean = false) : RunnerImpl() {

    override fun run(context: Context) {
        val cc = localBinDir().child("cc")
        if (cc.exists().not()) {
            cc.writeText(context.assets.open("terminal/cc.sh").bufferedReader()
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
                        args = arrayOf(cc.absolutePath, file.absolutePath),
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
set -e

# Check for missing packages
for pkg in ${"$"}required_packages; do
    if ! dpkg -s "${"$"}pkg" >/dev/null 2>&1; then
        missing_packages="${"$"}missing_packages ${"$"}pkg"
    fi
done

# Install missing packages
if [ -n "${"$"}missing_packages" ]; then
    echo -e "\e[34;1m[*]\e[37m Installing missing packages: ${"$"}missing_packages\e[0m"
    pkg install -y ${"$"}missing_packages
fi

TARGET_FILE=${file.absolutePath}

run_code() {
    echo -e "\e[32;1m[✓]\e[37m Compilation successful! Running...\e[0m"
    chmod +x "${"$"}OUTPUT_FILE"
    if [ -x "$file" ]; then
        "${"$"}OUTPUT_FILE"
    else
        mv "${"$"}OUTPUT_FILE" ${"$"}PREFIX/tmp/a.out
        chmod +x ${"$"}PREFIX/tmp/a.out
        ${"$"}PREFIX/tmp/a.out
    fi
}

# Process file or directory
if [ -f "${"$"}TARGET_FILE" ]; then
    EXT="${"$"}{TARGET_FILE##*.}"
    OUTPUT_FILE="${"$"}{TARGET_FILE%.*}"
    COMPILER="clang"
    
    if [[ "${"$"}EXT" == "cpp" || "${"$"}EXT" == "cc" ]]; then
        COMPILER="clang++"
    fi
    
    echo -e "\e[34;1m[*]\e[37m Compiling ${"$"}EXT file: ${"$"}TARGET_FILE\e[0m"
    if ${"$"}COMPILER -o "${"$"}OUTPUT_FILE" "${"$"}TARGET_FILE"; then
        run_code
    else
        echo -e "\e[31;1m[✗]\e[37m Compilation failed!\e[0m"
        exit 1
    fi

elif [ -d "${"$"}TARGET_FILE" ]; then
    if [ -f "${"$"}TARGET_FILE/Makefile" ]; then
        echo -e "\e[34;1m[*]\e[37m Building with Makefile\e[0m"
        if make -C "${"$"}TARGET_FILE"; then
            OUTPUT_FILE=${"$"}(find "${"$"}TARGET_FILE" -maxdepth 1 -type f -executable | head -n 1)
            [ -n "${"$"}OUTPUT_FILE" ] && run_code || echo -e "\e[31;1m[✗]\e[37m No executable found after make.\e[0m"
        else
            echo -e "\e[31;1m[✗]\e[37m Build failed!\e[0m"
            exit 1
        fi
    elif [ -f "${"$"}TARGET_FILE/CMakeLists.txt" ]; then
        echo -e "\e[34;1m[*]\e[37m Building with CMake\e[0m"
        mkdir -p "${"$"}TARGET_FILE/build"
        cd "${"$"}TARGET_FILE/build"
        if cmake .. && make; then
            OUTPUT_FILE=${"$"}(find . -maxdepth 1 -type f -executable | head -n 1)
            [ -n "${"$"}OUTPUT_FILE" ] && run_code || echo -e "\e[31;1m[✗]\e[37m No executable found after cmake build.\e[0m"
        else
            echo -e "\e[31;1m[✗]\e[37m Build failed!\e[0m"
            exit 1
        fi
    else
        echo "No Makefile or CMakeLists.txt found in ${"$"}TARGET_FILE"
        exit 1
    fi
else
    echo "File or directory not found: ${"$"}TARGET_FILE"
    exit 1
fi

    #todo : throw a intent or something to go back
    echo -e "\n\nProcess completed. Press Enter to go back"
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
        return drawables.ic_language_c.getDrawable(context)
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {

    }
}