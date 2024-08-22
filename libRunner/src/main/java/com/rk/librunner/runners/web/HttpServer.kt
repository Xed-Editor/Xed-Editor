package com.rk.librunner.runners.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.URLConnection


class HttpServer(port: Int, val basePath: File, val toOpenFile: File? = null,val DirectcontentResponse:StringBuilder? = null,val onResponse:((file: File?,response:StringBuilder) -> String)? = null) : NanoHTTPD(port) {
	
	override fun serve(session: IHTTPSession?): Response {
		val uri = session?.uri
		val responseString:StringBuilder
		var mimeType = "text/html"
		
		if (toOpenFile != null){
			var targetFile: File = toOpenFile
			if (uri != "/"){
				val targetPath = uri!!.removePrefix("/").replace("/", File.separator)
				var possibleFile = File(basePath, targetPath)
				
				if (!possibleFile.exists() && !targetPath.contains(".")) {
					possibleFile = File(basePath, "$targetPath.html")
				}
				
				if (!possibleFile.exists() || !possibleFile.isFile) {
					return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
				}
				
				targetFile = possibleFile
				mimeType = URLConnection.guessContentTypeFromName(targetFile.name)
			}
			responseString = readFile(targetFile)
		}else{
			responseString = DirectcontentResponse!!
		}
		
		
		
		
		return try {
			val response:String
			if (onResponse != null){
				response = onResponse.let { it(toOpenFile,responseString) }.toString()
			}else{
				response = responseString.toString()
			}
			
			
			newFixedLengthResponse(Response.Status.OK, mimeType, response)
		} catch (e: Exception) {
			newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.stackTrace.toString())
		}
	}
	private fun readFile(file: File):StringBuilder{
		val fileInputStream = FileInputStream(file)
		val reader = InputStreamReader(fileInputStream)
		val bufferedReader = BufferedReader(reader)
		
		val content = StringBuilder()
		bufferedReader.forEachLine { line ->
			content.append(line).append("\n")
		}
		
		bufferedReader.close()
		reader.close()
		fileInputStream.close()
		return content
	}
	
}