package com.rk.runner.runners.web.html

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.runner.runners.web.HttpServer
import com.rk.utils.toast

class HtmlRunner() : RunnerImpl() {
    companion object {
        var httpServer: HttpServer? = null
        private const val PORT = 8357
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        stop()
        httpServer = HttpServer(context, PORT, fileObject.getParentFile() ?: fileObject)

        val address = "http://localhost:$PORT"
        toast(strings.http_server_at.getFilledString(address))

        val url = "$address/${fileObject.getName()}"
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(context, url.toUri())
    }

    override fun getName(): String {
        return FileType.HTML.title
    }

    override fun getIcon(context: Context): Icon {
        return Icon.DrawableRes(drawables.ic_language_html)
    }

    override suspend fun isRunning(): Boolean = httpServer?.isAlive == true

    override suspend fun stop() {
        if (isRunning()) {
            httpServer?.closeAllConnections()
            httpServer?.stop()
        }
        httpServer = null
    }
}
