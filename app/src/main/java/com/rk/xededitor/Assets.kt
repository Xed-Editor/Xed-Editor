package com.rk.xededitor

import android.content.Context
import com.rk.libcommons.Decompress
import java.io.File

object Assets {
	fun verify(context: Context) {
		Thread {
			val externalFiles = context.filesDir
			val destination = File(externalFiles, "unzip")
			if (!destination.exists()) {
				try {
					Decompress.unzipFromAssets(context, "files.zip", destination.absolutePath)
					File(externalFiles, "files").delete()
					File(externalFiles, "files.zip").delete()
					File(externalFiles, "textmate").delete()
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
			
		}.start()
		
	}
}