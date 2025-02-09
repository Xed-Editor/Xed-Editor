package com.rk.runner.runners.web.markdown

import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.rk.file_wrapper.FileWrapper
import com.rk.runner.runners.web.HttpServer
import com.rk.runner.runners.web.WebActivity
import com.rk.runner.runners.web.html.HtmlRunner
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

private const val PORT = 8357

class MDViewer : WebActivity() {
    private lateinit var file: File
    private lateinit var httpServer: HttpServer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mdViewerRef = WeakReference(this)
        file = File(intent.getStringExtra("filepath").toString())
        
        supportActionBar!!.title = file.name
        val isDarkMode: Boolean =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        HtmlRunner.httpServer?.let {
            if(it.isAlive){
                it.stop()
            }
        }

        httpServer = HttpServer(PORT, FileWrapper(file.parentFile!!)) { md,session ->
            val parameters = session.parameters
            val pathAfterSlash = session.uri?.substringAfter("/") ?: ""
            if (parameters.containsKey("textmd")){
                return@HttpServer null
            }
            
            if (md.exists() && md.isFile() && md.getName().endsWith(".md")) {
                try {
                    val htmlString = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${md.getName().removeSuffix(".md")}</title>
            <script type="module" src="https://cdn.jsdelivr.net/npm/zero-md@3?register"></script>
        </head>
        <body style="background-color: ${if (isDarkMode){"#0D1117"}else{"#FFFFFF"}};">
             <zero-md src="/${pathAfterSlash}?textmd">
             
             </zero-md>
        </body>
        </html>
    """.trimIndent()
                    
                    return@HttpServer newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, "text/html", htmlString
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
            }
            return@HttpServer null
        }
        
        lifecycleScope.launch(Dispatchers.Default) {
            suspend fun fetchMarkdownFile(url: String): String {
                return withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    return@withContext try {
                        connection.requestMethod = "GET"
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        connection.disconnect()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                with(binding!!.webview) {
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "http://localhost:${PORT}",
                        fetchMarkdownFile("http://localhost:${PORT}/${file.name}"),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        if (httpServer.isAlive) {
            httpServer.stop()
        }
        super.onDestroy()
    }
}
