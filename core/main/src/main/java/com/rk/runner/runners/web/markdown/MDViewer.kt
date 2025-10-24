package com.rk.runner.runners.web.markdown

import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.rk.file.FileObject
import com.rk.runner.runners.web.HttpServer
import com.rk.runner.runners.web.WebActivity
import com.rk.runner.runners.web.WebScreen
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.xededitor.ui.theme.XedTheme
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

private const val PORT = 8357

class MDViewer : WebActivity() {
    private lateinit var file: FileObject
    private var httpServer: HttpServer? = null
    private var webViewRef: WeakReference<WebView>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mdViewerRef = WeakReference(this)
        file = toPreviewFile!!

        val isDarkMode: Boolean =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // kill any existing HtmlRunner server
        HtmlRunner.httpServer?.let {
            if (it.isAlive) it.stop()
        }


        // start markdown-serving server
        httpServer = HttpServer(PORT, file.getParentFile() ?: file) { md, session ->
            val parameters = session.parameters
            val pathAfterSlash = session.uri?.substringAfter("/") ?: ""
            if (parameters.containsKey("textmd")) {
                return@HttpServer null
            }

            if (md.exists() && md.isFile() && md.getName().endsWith(".md")) {
                val htmlString = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>${md.getName().removeSuffix(".md")}</title>
                        <script type="module" src="https://cdn.jsdelivr.net/npm/zero-md@3?register"></script>
                    </head>
                    <body style="background-color:${if (isDarkMode) "#0D1117" else "#FFFFFF"};">
                         <zero-md src="/$pathAfterSlash?textmd"></zero-md>
                    </body>
                    </html>
                """.trimIndent()

                return@HttpServer newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, "text/html", htmlString
                )
            }
            return@HttpServer null
        }

        // now load WebView inside Compose
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
                            val html = fetchMarkdownFile("http://localhost:$PORT/${file.getName()}")
                            withContext(Dispatchers.Main) {
                                webView.loadDataWithBaseURL(
                                    "http://localhost:$PORT",
                                    html,
                                    "text/html",
                                    "utf-8",
                                    null
                                )
                            }
                        }
                    }
                )
            }

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
