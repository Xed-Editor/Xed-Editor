package com.rk.commands.editor

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.rk.DefaultScope
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.file.FileWrapper
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.toast
import kotlinx.coroutines.launch

class ShareCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.share"

    override fun getLabel(): String = strings.share.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val activity = editorActionContext.currentActivity
        val file = editorActionContext.editorTab.file

        DefaultScope.launch {
            if (file.getAbsolutePath().contains(activity.filesDir.parentFile!!.absolutePath)) {
                toast(strings.permission_denied)
                return@launch
            }

            val fileUri =
                if (file is FileWrapper) {
                    FileProvider.getUriForFile(activity as Context, "${activity.packageName}.fileprovider", file.file)
                } else {
                    file.toUri()
                }

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = activity.contentResolver.getType(fileUri) ?: "*/*"
                    setDataAndType(fileUri, activity.contentResolver.getType(fileUri) ?: "*/*")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

            activity.startActivity(Intent.createChooser(intent, "Share file"))
        }
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.send)
}
