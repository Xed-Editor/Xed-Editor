package com.rk.runner.runners.web

import android.content.Context
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.amoled
import com.rk.utils.isDarkTheme
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URLConnection
import kotlinx.coroutines.runBlocking

class HttpServer(
    val context: Context,
    port: Int,
    val root: FileObject,
    val serveHook: ((FileObject, IHTTPSession) -> Response?)? = null,
) : NanoHTTPD(port) {
    init {
        start()
    }

    override fun serve(session: IHTTPSession?): Response {
        return runBlocking {
            val uri = session!!.uri

            if (root.isFile()) {
                if (!root.exists()) return@runBlocking notFoundError()
                return@runBlocking serveFile(root)
            }

            var file = root.getChildForName(uri)
            if (file.isDirectory()) {
                file = file.getChildForName("index.html")
            }

            // Hook override
            serveHook?.invoke(file, session)?.let {
                return@runBlocking it
            }

            if (!file.exists()) return@runBlocking notFoundError()

            return@runBlocking serveFile(file)
        }
    }

    private suspend fun serveFile(file: FileObject): Response {
        return if (Settings.inject_eruda) {
            serveFileWithEruda(file)
        } else {
            serveFileWithoutEruda(file)
        }
    }

    private suspend fun serveFileWithEruda(file: FileObject): Response {
        return try {
            val darkTheme = isDarkTheme(context)
            val erudaTheme = if (amoled.value && darkTheme) "AMOLED" else if (darkTheme) "Dark" else "Light"
            val erudaScript =
                """
                <!-- Injected code, this is not present in original code -->
                <script src="https://cdn.jsdelivr.net/npm/eruda"></script>
                <script>eruda.init({ theme: '$erudaTheme' });</script>
                <!-- Injected code, this is not present in original code -->
                """
                    .trimIndent()

            val html = file.getInputStream().bufferedReader().use { it.readText() }
            val injected =
                if (html.contains("</body>", ignoreCase = true)) {
                    html.replace("</body>", "$erudaScript</body>", ignoreCase = true)
                } else {
                    html + erudaScript
                }

            newFixedLengthResponse(
                Status.OK,
                URLConnection.guessContentTypeFromName(file.getName()) ?: "text/html",
                injected,
            )
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private suspend fun serveFileWithoutEruda(file: FileObject): Response {
        return try {
            newFixedLengthResponse(
                Status.OK,
                URLConnection.guessContentTypeFromName(file.getName()) ?: "application/octet-stream",
                file.getInputStream(),
                file.length(),
            )
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private fun notFoundError(): Response = newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not found")

    private fun forbiddenError(file: FileObject): Response =
        newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "403 Forbidden: Cannot read file ${file.getName()}")

    private fun internalError(e: Exception): Response =
        newFixedLengthResponse(
            Status.INTERNAL_ERROR,
            "text/plain",
            "500 Internal server error: ${e.localizedMessage ?: strings.unknown.getString()}",
        )
}
