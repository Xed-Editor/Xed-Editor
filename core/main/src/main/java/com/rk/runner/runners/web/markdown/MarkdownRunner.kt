package com.rk.runner.runners.web.markdown

import android.content.Context
import android.content.Intent
import com.rk.file.BuiltinFileType
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.Runner
import com.rk.runner.runners.web.html.HtmlRunner
import java.lang.ref.WeakReference

var mdViewerRef = WeakReference<MDViewer?>(null)
var toPreviewFile: FileObject? = null

object MarkdownRunner : Runner() {

    override val id = "markdown_preview"
    override val label = strings.markdown_preview.getString()
    override val description = strings.markdown_preview_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        val markdownExtensions = BuiltinFileType.MARKDOWN.extensions.joinToString("|")
        return Regex(".*\\.($markdownExtensions)$").matches(fileObject.getName())
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        val intent = Intent(context, MDViewer::class.java)
        toPreviewFile = fileObject
        context.startActivity(intent)
    }

    override fun getIcon(context: Context): Icon? {
        return BuiltinFileType.MARKDOWN.icon
    }

    override suspend fun isRunning(): Boolean {
        return mdViewerRef.get() != null
    }

    override suspend fun stop() {
        HtmlRunner.httpServer?.let {
            it.closeAllConnections()
            if (it.isAlive) {
                it.stop()
            }
        }
        HtmlRunner.httpServer = null
        mdViewerRef.get()?.finish()
        mdViewerRef = WeakReference(null)
    }
}
