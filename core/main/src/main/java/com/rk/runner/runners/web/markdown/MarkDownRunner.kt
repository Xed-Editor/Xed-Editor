package com.rk.runner.runners.web.markdown

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.file_wrapper.FileObject
import com.rk.runner.RunnerImpl
import com.rk.xededitor.R
import java.lang.ref.WeakReference

var mdViewerRef = WeakReference<MDViewer?>(null)
var toPreviewFile: FileObject? = null

class MarkDownRunner(val file: FileObject) : RunnerImpl() {

    override fun run(context: Context) {
        val intent = Intent(context, MDViewer::class.java)
        toPreviewFile = file
        context.startActivity(intent)
    }

    override fun getName(): String {
        return "MarkDown"
    }

    override fun getDescription(): String {
        return "Preview markdown"
    }

    override fun getIcon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.markdown)
    }

    override fun isRunning(): Boolean {
        return mdViewerRef.get() != null
    }

    override fun stop() {
        mdViewerRef.get()?.finish()
        mdViewerRef = WeakReference(null)
    }
}
