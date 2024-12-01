package com.rk.runner.runners.node

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.resources.drawables
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.extractAssets
import java.io.File

class NodeRunner : RunnerImpl {
    override fun run(file: File, context: Context) {
        extractAssets(context) {
        
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
