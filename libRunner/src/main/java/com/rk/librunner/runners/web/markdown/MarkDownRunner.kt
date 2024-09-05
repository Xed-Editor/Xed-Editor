package com.rk.librunner.runners.web.markdown

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.librunner.R
import com.rk.librunner.RunnableInterface
import java.io.File

class MarkDownRunner : RunnableInterface {
	override fun run(file: File, context: Context) {
		val intent = Intent(context, MDViewer::class.java)
		intent.putExtra("filepath",file.absolutePath)
		context.startActivity(intent)
	}
	
	override fun getName(): String {
		return "MarkDown"
	}
	
	override fun getDescription(): String {
		return "preview markdown"
	}
	
	override fun getIcon(context: Context): Drawable? {
		return ContextCompat.getDrawable(context, R.drawable.markdown)
	}
}