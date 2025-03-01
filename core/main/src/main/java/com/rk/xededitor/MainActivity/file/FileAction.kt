package com.rk.xededitor.MainActivity.file

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.askInput
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.ui.activities.terminal.Terminal
import com.rk.xededitor.ui.screens.settings.feature_toggles.Features
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util.copyStream

class FileAction(
    private val mainActivity: MainActivity,
    private val rootFolder: FileObject,
    private val file: FileObject,
) {

    companion object {
        var to_save_file: FileObject? = null
    }
    
    private fun getString(@StringRes id:Int):String{
        return id.getString()
    }

    init {
        fun getDrawable(id: Int): Drawable? {
            return ContextCompat.getDrawable(mainActivity, id)
        }
        

        ActionPopup(mainActivity, true).apply {
            if (file.isDirectory()){
                if (file == rootFolder) {
                    addItem(
                        strings.close.getString(),
                        getString(strings.close_current_project),
                        getDrawable(drawables.close),
                    ) {
                        ProjectManager.removeProject(
                            mainActivity, rootFolder
                        )
                    }
                }

                addItem(
                    strings.refresh.getString(),
                    strings.reload_file_tree.getString(),
                    getDrawable(drawables.refresh),
                ) {
                    mainActivity.lifecycleScope.launch {
                        ProjectManager.CurrentProject.refresh(mainActivity, parent = file)
                    }
                }

                addItem(strings.new_document.getString(),
                    strings.new_document_desc.getString(),
                    getDrawable(drawables.add),
                ) { new() }

            }

            addItem(
                getString(strings.rename),
                getString(strings.rename_descript),
                getDrawable(drawables.edit),
            ) {
                rename()
            }
            addItem(
                getString(strings.open_with),
                getString(strings.open_with_other),
                getDrawable(drawables.android),
            ) {
                openWith(mainActivity, file)
            }

            addItem(
                getString(strings.add_file),
                getString(strings.add_file_desc),
                getDrawable(drawables.outline_insert_drive_file_24),
            ) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                mainActivity.fileManager?.parentFile = file
                mainActivity.fileManager?.requestAddFile?.launch(intent)
            }

            if (file is FileWrapper && file.isDirectory() && Features.terminal.value){
                addItem(
                    getString(strings.open_in_terminal),
                    getString(strings.open_dir_in_terminal),
                    getDrawable(drawables.terminal),
                ) {
                    val intent = Intent(context, Terminal::class.java)
                    intent.putExtra("cwd", file.getAbsolutePath())
                    context.startActivity(intent)
                }
            }

            addItem(
                getString(strings.delete),
                getString(strings.delete_descript),
                getDrawable(drawables.delete),
            ) {
                MaterialAlertDialogBuilder(context).setTitle(getString(strings.delete))
                    .setMessage(getString(strings.ask_del) + " ${file.getName()} ")
                    .setNegativeButton(getString(strings.cancel), null)
                    .setPositiveButton(getString(strings.delete)) { _: DialogInterface?, _: Int ->
                        val loading = LoadingPopup(mainActivity, null).show()
                        ProjectManager.CurrentProject.updateFileDeleted(
                            mainActivity, file
                        )
                        mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                val success = file.delete()
                                withContext(Dispatchers.Main) {
                                    loading.hide()
                                    if (success.not()) {
                                        toast("Failed to delete file")
                                    }
                                }
                            }
                        }
                    }.show()
            }

            if (file is FileWrapper && file.isDirectory()) {
                addItem(title = strings.clone_repo.getString(),
                    description = strings.clone_repo_desc.getString(),
                    icon = getDrawable(drawables.github),
                    listener = {
                        cloneRepo()
                    })

            }

            addItem(getString(strings.copy), getString(strings.copy_desc), if (file.isDirectory()){
                drawables.folder_copy_24px.getDrawable(mainActivity)
            }else{
                drawables.content_copy_24px.getDrawable(mainActivity)
            }) {
                FileClipboard.setFile(file)
            }

            if (file.isDirectory()){
                addItem(
                    getString(strings.paste),
                    getString(strings.paste_desc),
                    drawables.content_paste_24px.getDrawable(mainActivity),
                ) {
                    if (FileClipboard.isEmpty()) {
                        toast(getString(strings.clipboardempty))
                    } else {
                        val loading = LoadingPopup(mainActivity, null).show()

                        mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                            if (!FileClipboard.isEmpty()) {
                                val source = FileClipboard.getFile()
                                if (file.isDirectory() && source != null) {
                                    runCatching {

                                        fun copy(sourceFile: FileObject, targetFolder: FileObject) {
                                            if (!targetFolder.canWrite()) {
                                                throw IllegalStateException("Target directory is not writable")
                                            }

                                            if (sourceFile.isDirectory()) {
                                                sourceFile.listFiles().forEach { sourceFileChild ->
                                                    copy(
                                                        sourceFileChild, targetFolder.createChild(
                                                            false, sourceFile.getName()
                                                        )!!
                                                    )
                                                }
                                            } else {
                                                context.contentResolver.openInputStream(sourceFile.toUri())
                                                    .use { inputStream ->
                                                        context.contentResolver.openOutputStream(
                                                            targetFolder.createChild(
                                                                true, sourceFile.getName()
                                                            )!!.toUri()
                                                        )?.use { outputStream ->
                                                            copyStream(inputStream, outputStream)
                                                        }
                                                    }
                                            }
                                        }

                                        copy(source, file)
                                    }.onFailure {
                                        it.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            toast(it.message)
                                            loading.hide()
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        loading.hide()
                                    }

                                    FileClipboard.clear()
                                }
                            }
                        }
                    }
                }
            }

            if (file.isFile()){
                addItem(
                    getString(strings.save_as),
                    getString(strings.save_desc),
                    getDrawable(drawables.save),
                ) {
                    to_save_file = file
                    MainActivity.activityRef.get()?.fileManager?.requestOpenDirectoryToSaveFile()
                }
            }

        }.show()
    }

    private fun new() {

        fun create(createFile: Boolean,name: String){
            if (name.isEmpty()) {
                toast(mainActivity.getString(strings.ask_enter_name))
                return
            }

            val loading = LoadingPopup(mainActivity, null)
            loading.show()


            mainActivity.lifecycleScope.launch(Dispatchers.Default) {
                runCatching {
                    if (file.hasChild(name)) {
                        withContext(Dispatchers.Main) {
                            toast(mainActivity.getString(strings.already_exists))
                            loading.hide()
                        }
                    }

                    file.createChild(createFile, name)
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        loading.hide()
                        ProjectManager.CurrentProject.updateFileAdded(mainActivity, file)
                    }
                }.onFailure {
                    it.printStackTrace()
                    withContext(Dispatchers.Main) {
                        loading.hide()
                        toast(it.message)
                    }
                }
            }
        }


        MaterialAlertDialogBuilder(mainActivity).apply {
            setTitle(strings.new_document)
            val popupView: View = LayoutInflater.from(mainActivity).inflate(R.layout.popup_new, null)
            val editText = popupView.findViewById<EditText>(R.id.name)
            editText.hint = mainActivity.getString(strings.newFile_hint)
            setView(popupView)
            setNeutralButton(strings.cancel, null)
            setNegativeButton(strings.file){ _, _ ->
                create(true,editText.text.toString())
            }
            setPositiveButton(strings.folder){ _, _ ->
                create(false,editText.text.toString())
            }
            show()
        }
    }

    private fun rename() {
        mainActivity.askInput(
            title = strings.rename.getString(),
            input = file.getName(),
            hint = strings.file_name.getString(),
            onResult = { input ->
                if (input.isEmpty()) {
                    toast(mainActivity.getString(strings.ask_enter_name))
                    return@askInput
                }

                val loading = LoadingPopup(mainActivity, null)
                loading.show()

                mainActivity.lifecycleScope.launch(Dispatchers.Default) {

                    runCatching {
                        if (file.hasChild(input)) {
                            withContext(Dispatchers.Main) {
                                toast(mainActivity.getString(strings.already_exists))
                                loading.hide()
                            }
                            return@launch
                        }

                        val success = file.renameTo(input)

                        if (success.not()) {
                            throw IllegalStateException("Unable to rename file")
                        }

                        withContext(Dispatchers.Main) {
                            ProjectManager.CurrentProject.updateFileRenamed(
                                mainActivity, file, file
                            )
                        }
                    }.onFailure {
                        it.printStackTrace()
                        withContext(Dispatchers.Main) {
                            loading.hide()
                            toast(it.message.toString())
                        }

                    }.onSuccess {
                        withContext(Dispatchers.Main) {
                            loading.hide()
                        }
                    }


                }
            }
        )
    }

    private fun openWith(context: Context, file: FileObject) {

        try {
            val uri: Uri = when (file) {
                is UriWrapper -> {
                    file.toUri()
                }

                is FileWrapper -> {
                    FileProvider.getUriForFile(
                        context,
                        context.applicationContext.packageName + ".fileprovider",
                        file.file,
                    )
                }

                else -> {
                    throw RuntimeException("Unsupported FileObject")
                }
            }

            val mimeType = file.getMimeType(context)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, getString(strings.canthandle), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(strings.file_open_denied))
        }
    }


    private fun cloneRepo() {
        if (file is FileWrapper){
            mainActivity.askInput(
                title = "Clone",
                hint = "repository url",
                onResult = { input ->
                    if (input.isEmpty()) {
                        toast("Invalid url")
                        return@askInput
                    }

                    val loading = LoadingPopup(mainActivity, null)
                    loading.show()

                    mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                        GitClient.clone(mainActivity, input, file.file, onResult = {
                            runOnUiThread {
                                if (it == null) {
                                    mainActivity.lifecycleScope.launch {
                                        ProjectManager.CurrentProject.updateFileAdded(
                                            mainActivity,
                                            file
                                        )
                                    }
                                }
                                loading.hide()
                                toast(it?.message)
                            }
                        })

                    }
                }
            )
        }else{
            toast("Unsupported file type")
        }

    }
}