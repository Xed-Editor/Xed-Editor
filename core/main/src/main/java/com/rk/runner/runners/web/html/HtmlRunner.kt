package com.rk.runner.runners.web.html

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.rk.file_wrapper.FileObject
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runner.RunnerImpl
import com.rk.runner.runners.web.HttpServer


class HtmlRunner(val file: FileObject) : RunnerImpl() {
    companion object {
        var httpServer: HttpServer? = null
        private const val PORT = 8357
    }

    //a broadcasts should be used instead of this hack
//    class DevTools : Service(){
//        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
//        override fun onBind(p0: Intent?): IBinder? {
//            return null
//        }
//    }

    override fun run(context: Context) {
        stop()
        httpServer = HttpServer(PORT, file.getParentFile()!!)
        val url = "http://localhost:$PORT/${file.getName()}"
        CustomTabsIntent.Builder().apply {
            setShowTitle(true)
            setShareState(CustomTabsIntent.SHARE_STATE_OFF)

//            val pendingIntent = PendingIntent.getService(
//                context, 0, Intent(context,DevTools::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//            )
//
//            addMenuItem("Dev Tools",pendingIntent)
        }.build().launchUrl(context, Uri.parse(url))
    }

    override fun getName(): String {
        return "Html"
    }

    override fun getDescription(): String {
        return "Preview html"
    }

    override fun getIcon(context: Context): Drawable? =
        drawables.ic_language_html.getDrawable(context)

    override fun isRunning(): Boolean = httpServer?.isAlive == true

    override fun stop() {
        if (isRunning()) {
            httpServer?.stop()
        }
    }
}
