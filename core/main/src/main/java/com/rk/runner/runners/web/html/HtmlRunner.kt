package com.rk.runner.runners.web.html

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.icons.Icon
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.runner.runners.web.HttpServer
import com.rk.settings.Settings
import com.rk.utils.toast
import java.net.BindException

class HtmlRunner() : RunnerImpl() {
    companion object {
        var httpServer: HttpServer? = null
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        stop()

        val port = Settings.http_server_port
        try {
            httpServer = HttpServer(context, port, fileObject.getParentFile() ?: fileObject)
        } catch (_: BindException) {
            toast(strings.http_server_port_error.getFilledString(port))
            return
        }

        val address = "http://localhost:$port"
        toast(strings.http_server_at.getFilledString(address))

        val url = "$address/${fileObject.getName()}"
        if (Settings.launch_in_browser) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
            return
        }
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(context, url.toUri())
    }

    override fun getName(): String {
        return strings.html_preview.getString()
    }

    override fun getIcon(context: Context): Icon {
        return Icon.DrawableRes(FileType.HTML.icon!!)
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
