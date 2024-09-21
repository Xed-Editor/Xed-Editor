package com.rk.xededitor

import android.content.Context
import com.rk.libcommons.Decompress
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

object Assets {
	@OptIn(DelicateCoroutinesApi::class)
	fun verify(context: Context) {
		GlobalScope.launch(Dispatchers.IO){
			val externalFiles = context.filesDir.parentFile
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
		}
	}
}