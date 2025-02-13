package com.rk.xededitor.MainActivity.tabs.media

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.rk.file_wrapper.FileObject
import com.rk.runner.runners.web.HttpServer
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.URLEncoder

class WebFragment(val context: Context,val scope: CoroutineScope) : CoreFragment {
    private var file: FileObject? = null
    private val webView:WebView = WebView(context)
    private var httpServer:HttpServer? = null
    private val port by lazy { ServerSocket(0).use { socket -> socket.localPort } }

    override fun getView(): View {
        return webView
    }
    
    override fun onDestroy() {
        webView.destroy()
        if (httpServer?.isAlive == true) {
            httpServer?.stop()
        }
    }
    
    override fun onClosed() {
        onDestroy()
    }
    override fun onCreate() {
        webView.setWebChromeClient(WebChromeClient())
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
    }
    
    override fun loadFile(file: FileObject) {
        this.file = file
        scope.launch(Dispatchers.IO) {
            httpServer = HttpServer(port, file.getParentFile()!!)
            withContext(Dispatchers.Main){
                webView.loadUrl("http://localhost:$port/${
                    withContext(Dispatchers.IO) {
                        URLEncoder.encode(file.getName(), "UTF-8")
                    }
                }")
            }
        }
    }
    
    override fun getFile(): FileObject? {
        return file
    }
}