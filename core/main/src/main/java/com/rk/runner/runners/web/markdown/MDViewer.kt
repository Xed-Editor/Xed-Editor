package com.rk.runner.runners.web.markdown

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.rk.file.FileObject
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.runner.runners.web.HttpServer
import com.rk.runner.runners.web.WebActivity
import com.rk.runner.runners.web.WebScreen
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.settings.Settings
import com.rk.theme.XedTheme
import com.rk.utils.isDarkTheme
import com.rk.utils.toast
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.lang.ref.WeakReference
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MDViewer : WebActivity() {
    private lateinit var file: FileObject
    private var httpServer: HttpServer? = null
    private var webViewRef: WeakReference<WebView>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mdViewerRef = WeakReference(this)
        file = toPreviewFile!!

        val isDarkMode: Boolean = isDarkTheme(this)
        val port = Settings.http_server_port

        // Kill any existing HtmlRunner server
        HtmlRunner.httpServer?.let { if (it.isAlive) it.stop() }

        lifecycleScope.launch {
            try {
                httpServer = getHttpServer(port, isDarkMode)

                // Now load WebView inside Compose
                setContent {
                    XedTheme {
                        WebScreen(
                            title = file.getName(),
                            onBackPressed = { onBackPressedDispatcher.onBackPressed() },
                            setupWebView = { webView ->
                                setupWebView(webView)
                                webView.webViewClient = WebViewClient()
                                webViewRef = WeakReference(webView)

                                lifecycleScope.launch(Dispatchers.Default) {
                                    val html = fetchMarkdownFile("http://localhost:$port/${file.getName()}")
                                    withContext(Dispatchers.Main) {
                                        webView.loadDataWithBaseURL(
                                            "http://localhost:$port",
                                            html,
                                            "text/html",
                                            "utf-8",
                                            null,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            } catch (_: BindException) {
                toast(strings.http_server_port_error.getFilledString(port))
                return@launch
            }
        }
    }

    private suspend fun getHttpServer(port: Int, isDarkMode: Boolean): HttpServer =
        HttpServer(this@MDViewer, port, file.getParentFile() ?: file) { md, session ->
            return@HttpServer runBlocking {
                val parameters = session.parameters
                val pathAfterSlash = session.uri?.substringAfter("/") ?: ""
                if (parameters.containsKey("textmd")) {
                    return@runBlocking null
                }

                if (md.exists() && md.isFile() && md.getName().endsWith(".md")) {
                    val htmlString =
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>${md.getName().removeSuffix(".md")}</title>
                        </head>
                        <script type="module">
                          import ZeroMd, { STYLES } from 'https://cdn.jsdelivr.net/npm/zero-md@3'
    
                          customElements.define(
                            'zero-md',
                            class extends ZeroMd {
                              async load() {
                                await super.load()
                                this.template = STYLES.preset('${if (isDarkMode) "dark" else "light"}')
                              }
                            }
                          )
                        </script>
                        <body style="background-color: ${if (isDarkMode) "#0D1117" else "#FFFFFF"};">
                             <zero-md src="/$pathAfterSlash?textmd"></zero-md>
                        </body>
                        </html>
                        """
                            .trimIndent()

                    return@runBlocking newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html", htmlString)
                }
                return@runBlocking null
            }
        }

    private suspend fun fetchMarkdownFile(url: String): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    override fun onDestroy() {
        if (httpServer?.isAlive == true) {
            httpServer?.stop()
        }
        httpServer = null

        HtmlRunner.httpServer?.let {
            it.closeAllConnections()
            if (it.isAlive) it.stop()
        }
        HtmlRunner.httpServer = null

        mdViewerRef.get()?.finish()
        mdViewerRef = WeakReference(null)

        webViewRef?.clear()
        webViewRef = null

        super.onDestroy()
    }
}
