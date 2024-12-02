package com.rk.runner.runners.node

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.isExecPermissionGranted
import com.rk.karbon_exec.isTermuxCompatible
import com.rk.karbon_exec.isTermuxInstalled
import com.rk.karbon_exec.runBashScript
import com.rk.karbon_exec.testExecPermission
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.extractAssets
import java.io.File

class NodeRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        if (!(isTermuxInstalled() && isExecPermissionGranted() && isTermuxCompatible() && testExecPermission().first)) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Termux-Exec is not enabled", Toast.LENGTH_SHORT).show() }
        }
        
        extractAssets(context) {
            runBashScript(
                context, script = """
                cd ${file.parentFile.absolutePath}
                
                if command -v node >/dev/null 2>&1; then
                    echo "Node.js is installed, version: ${'$'}(node -v)"
                    node ${file.absolutePath}
                else
                    echo "Node.js is not installed, Installing..."
                    pkg i nodejs -y
                    node ${file.absolutePath}
                fi
                
                
                echo "Process Completed (Press Enter to exit)"
                read -r
                
                """, background = false
            )
        }
    }
    
    override fun getName(): String {
        return "Nodejs"
    }
    
    override fun getDescription(): String {
        return "Nodejs v20"
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
