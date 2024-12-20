package com.rk.runner.runners.node

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import java.io.File

class NodeRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        val node = File(context.localBinDir(),"node")
        if (node.exists().not()){
            node.writeText(context.assets.open("terminal/node.sh").bufferedReader().use { it.readText() })
        }
        launchInternalTerminal(
            context = context,
            shell = "/bin/sh",
            arrayOf("-c",node.absolutePath),
            id = "node",
            workingDir = file.parentFile.absolutePath
        )
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
