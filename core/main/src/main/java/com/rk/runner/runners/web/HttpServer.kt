package com.rk.runner.runners.web

import com.rk.file_wrapper.FileObject
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URLConnection
import java.util.Date

class HttpServer(
    port: Int,
    val rootDir: FileObject,
    val serveHook: ((FileObject, IHTTPSession) -> Response?)? = null
) : NanoHTTPD(port) {
    init {
        if (rootDir.isDirectory().not()) {
            throw RuntimeException("Expected a directory but got file")
        }
        start()
    }

    override fun serve(session: IHTTPSession?): Response {
        val uri = session!!.uri
        var file = rootDir.getChildForName(uri)
        if (file.isDirectory()) {
            file = file.getChildForName("index.html")
        }

        if (serveHook != null) {
            val response = serveHook.invoke(file, session)
            if (response != null) {
                return response
            }
        }

        if (file.exists().not()) {
            return newFixedLengthResponse(
                Status.NOT_FOUND,
                "text/plain",
                "404 not found " + Date().toString(),
            )
        }


        try {
            return newFixedLengthResponse(
                Status.OK,
                URLConnection.guessContentTypeFromName(file.getName()),
                file.getInputStream(),
                file.length(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return super.serve(session)
    }
}
