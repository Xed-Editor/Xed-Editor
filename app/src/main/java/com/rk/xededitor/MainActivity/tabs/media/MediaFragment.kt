package com.rk.xededitor.MainActivity.tabs.media

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.rk.libcommons.CustomScope
import com.rk.runner.commonUtils.getAvailablePort
import com.rk.runner.runners.web.HttpServer
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaFragment(val context: Context) : CoreFragment {
    val scope = CustomScope()
    @JvmField var file: File? = null
    private val webView:WebView = WebView(context)
    private var httpServer:HttpServer? = null
    private val port = getAvailablePort()
    
    override fun getView(): View {
        return webView
    }
    
    override fun onDestroy() {
        webView.destroy()
        if (httpServer?.isAlive == true) {
            httpServer?.stop()
        }
        scope.cancel()
    }
    
    override fun onCreate() {
        webView.setWebChromeClient(WebChromeClient())
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
    }
    
    override fun loadFile(file: File) {
        this.file = file
        scope.launch(Dispatchers.IO) {
            httpServer = HttpServer(port, file.parentFile!!)
            withContext(Dispatchers.Main){
                webView.loadUrl("http://localhost:$port/${file.name}")
            }
        }
    }
    
    override fun getFile(): File? {
        return file
    }
}