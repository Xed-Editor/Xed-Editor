package com.rk.runner.runners.web.html

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.rk.runner.RunnerImpl
import com.rk.runner.commonUtils.getAvailablePort
import com.rk.runner.runners.web.HttpServer
import java.io.File


class HtmlRunner : RunnerImpl {
    private var httpServer: HttpServer? = null
    private val port by lazy { getAvailablePort() }
    
    override fun run(file: File, context: Context) {
        stop()
        httpServer = HttpServer(port,file.parentFile!!)
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

    override fun getIcon(context: Context): Drawable? {
        return null
    }
    
    override fun isRunning(): Boolean {
        return httpServer?.isAlive == true
    }
    
    override fun stop() {
        if (httpServer?.isAlive == true){
            httpServer?.stop()
        }
    }
}
