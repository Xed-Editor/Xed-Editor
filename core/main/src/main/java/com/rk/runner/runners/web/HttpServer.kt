package com.rk.runner.runners.web

import com.rk.file.FileObject
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URLConnection
import java.util.Date
import kotlinx.coroutines.runBlocking

class HttpServer(port: Int, val root: FileObject, val serveHook: ((FileObject, IHTTPSession) -> Response?)? = null) :
    NanoHTTPD(port) {
    init {
        start()
    }

    override fun serve(session: IHTTPSession?): Response {
        return runBlocking {
            val uri = session!!.uri

            if (root.isFile()) {
                if (!root.exists()) {
                    return@runBlocking newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 not found ${Date()}")
                }

                return@runBlocking try {
                    newFixedLengthResponse(
                        Status.OK,
                        URLConnection.guessContentTypeFromName(root.getName()) ?: "application/octet-stream",
                        root.getInputStream(),
                        root.length(),
                    )
                } catch (e: SecurityException) {
                    newFixedLengthResponse(
                        Status.FORBIDDEN,
                        "text/plain",
                        "403 forbidden: cannot read file ${root.getName()}",
                    )
                } catch (e: Exception) {
                    newFixedLengthResponse(
                        Status.INTERNAL_ERROR,
                        "text/plain",
                        "500 internal error: ${e.message ?: "unknown"}",
                    )
                }
            }

            var file = root.getChildForName(uri)
            if (file.isDirectory()) {
                file = file.getChildForName("index.html")
            }

            // Hook override
            serveHook?.invoke(file, session)?.let {
                return@runBlocking it
            }

            if (!file.exists()) {
                return@runBlocking newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 not found ${Date()}")
            }

            return@runBlocking try {
                newFixedLengthResponse(
                    Status.OK,
                    URLConnection.guessContentTypeFromName(file.getName()) ?: "application/octet-stream",
                    file.getInputStream(),
                    file.length(),
                )
            } catch (e: SecurityException) {
                newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "403 forbidden: cannot read ${file.getName()}")
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Status.INTERNAL_ERROR,
                    "text/plain",
                    "500 internal error: ${e.message ?: "unknown"}",
                )
            }
        }
    }
}
