package com.rk.runner.runners.web.html

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.rk.file.FileObject
import com.rk.libcommons.errorDialog
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.runner.runners.web.HttpServer
import androidx.core.net.toUri
import com.rk.libcommons.toast


class HtmlRunner() : RunnerImpl() {
    companion object {
        var httpServer: HttpServer? = null
        private const val PORT = 8357
    }

    //a broadcasts should be used instead of this hack
//    class DevTools : Service(){
//        override suspend fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//            httpServer?.let {
//                if (it.isAlive.not()){
//                    stopSelf()
//                }
//
//                //how do you even refresh?? only if chrome custom tab had a simple api
//            }
//            stopSelf()
//            return super.onStartCommand(intent, flags, startId)
//        }
//        override suspend fun onBind(p0: Intent?): IBinder? {
//            return null
//        }
//    }

    override suspend fun run(context: Context,file: FileObject) {
        stop()
        httpServer = HttpServer(PORT, file.getParentFile() ?: file)

        toast("Serving at: http://localhost:$PORT")

        val url = "http://localhost:$PORT/${file.getName()}"
        CustomTabsIntent.Builder().apply {
            setShowTitle(true)
            setShareState(CustomTabsIntent.SHARE_STATE_OFF)

//            val pendingIntent = PendingIntent.getService(
//                context, 0, Intent(context,DevTools::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//            )
//
//            addMenuItem("Dev Tools",pendingIntent)
        }.build().launchUrl(context, url.toUri())
    }

    override suspend fun getName(): String {
        return "Html"
    }

    override suspend fun getIcon(context: Context): Drawable? =
        drawables.ic_language_html.getDrawable(context)

    override suspend fun isRunning(): Boolean = httpServer?.isAlive == true

    override suspend fun stop() {
        if (isRunning()) {
            httpServer?.closeAllConnections()
            httpServer?.stop()
        }
        httpServer = null
    }
}
