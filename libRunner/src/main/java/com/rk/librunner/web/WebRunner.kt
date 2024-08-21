package com.rk.librunner.web

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.rk.librunner.RunnableInterface
import com.rk.librunner.markdown.MarkDownPreview
import java.io.File

class WebRunner : RunnableInterface {
	override fun run(file: File, context: Context) {
		val intent = Intent(context, WebViewActivity::class.java)
		intent.putExtra("filepath",file.absolutePath)
		context.startActivity(intent)
	}
	
	override fun getName(): String {
		return "WebRunner"
	}
	
	override fun getDescription(): String {
		return "preview html"
	}
	
	override fun getIcon(context: Context): Drawable? {
		return null
	}
}