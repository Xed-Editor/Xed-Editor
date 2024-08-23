package com.rk.librunner.runners.web.html

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.util.Date

class HttpServer(port:Int, val rootDir:File) : NanoHTTPD(port) {
	init {
		if (rootDir.isDirectory.not()){
			throw RuntimeException("Expected a directory but got file")
		}
		start()
	}
	override fun serve(session: IHTTPSession?): Response {
		val uri = session!!.uri
		var file = File(rootDir,uri)
		if (file.isDirectory){
			file = File(file,"index.html")
		}
		if (file.exists().not()){
			return newFixedLengthResponse(Status.NOT_FOUND,"text/plain","404 not found "+Date().toString())
		}

		try {
			return newFixedLengthResponse(Status.OK,URLConnection.guessContentTypeFromName(file.name),FileInputStream(file),file.length())
		}catch (e:Exception){
			e.printStackTrace()
		}
		
		return super.serve(session)
	}
}