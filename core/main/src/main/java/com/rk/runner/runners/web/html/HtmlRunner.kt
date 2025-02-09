package com.rk.runner.runners.web.html

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runner.RunnerImpl
import com.rk.runner.runners.web.HttpServer
import java.io.File
import java.net.ServerSocket

class HtmlRunner : RunnerImpl {
    private var httpServer: HttpServer? = null
    private val port by lazy { ServerSocket(0).use { socket -> socket.localPort } }
    
    override fun run(file: File, context: Context) {
        stop()
        httpServer = HttpServer(port,FileWrapper(file.parentFile!!))
        val url = "http://localhost:$port/${file.name}"
        val builder = CustomTabsIntent.Builder()
        builder.setShowTitle(true)
        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        val intent = builder.build()
        intent.launchUrl(context, Uri.parse(url))
    }

    override fun getName(): String {
        return "WebRunner"
    }

    override fun getDescription(): String {
        return "preview html"
    }

    override fun getIcon(context: Context): Drawable? = drawables.ic_language_html.getDrawable()
    
    override fun isRunning(): Boolean {
        return httpServer?.isAlive == true
    }
    
    override fun stop() {
        if (httpServer?.isAlive == true){
            httpServer?.stop()
        }
    }
}
