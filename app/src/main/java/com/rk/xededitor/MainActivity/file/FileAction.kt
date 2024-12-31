package com.rk.xededitor.MainActivity.file

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.filetree.provider.FileWrapper
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.child
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.R
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import com.rk.xededitor.rkUtils.runOnUiThread
import com.rk.xededitor.rkUtils.toastIt
import com.rk.xededitor.ui.activities.terminal.Terminal
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val REQUEST_ADD_FILE = 38758
const val REQUEST_CODE_OPEN_DIRECTORY = 8359487

class FileAction(
    private val mainActivity: MainActivity,
    private val rootFolder: File,
    private val file: File,
) {

    companion object {
        var to_save_file: File? = null
    }

    init {
        fun getDrawable(id: Int): Drawable? {
            return ContextCompat.getDrawable(mainActivity, id)
        }

        ActionPopup(mainActivity, true)
            .apply {
                if (file == rootFolder) {
                    addItem(
                        getString(strings.refresh),
                        getString(strings.reload_file_tree),
                        getDrawable(drawables.sync),
                    ) {
                        ProjectManager.currentProject.refresh(mainActivity)
                    }
                    addItem(
                        getString(strings.close),
                        getString(strings.close_current_project),
                        getDrawable(drawables.close),
                    ) {
                        ProjectManager.removeProject(mainActivity,FileWrapper(rootFolder))
                    }
                } else {
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
                        getString(strings.delete),
                        getString(strings.delete_descript),
                        getDrawable(drawables.delete),
                    ) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(getString(strings.delete))
                            .setMessage(getString(strings.ask_del) + " ${file.name} ")
                            .setNegativeButton(getString(strings.cancel), null)
                            .setPositiveButton(getString(strings.delete)) { _: DialogInterface?, _: Int ->
                                val loading = LoadingPopup(mainActivity, null).show()
                                ProjectManager.currentProject.updateFileDeleted(mainActivity, FileWrapper(file))
                                mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        if (file.isFile) {
                                            Files.delete(file.toPath())
                                        } else {
                                            Files.walk(file.toPath())
                                                .sorted(Comparator.reverseOrder())
                                                .forEach { Files.delete(it) }
                                        }
                                        withContext(Dispatchers.Main) { loading.hide() }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            loading.hide()
                                            rkUtils.toast("${getString(strings.failed_move)}: ${e.message}")
                                        }
                                    }
                                }
                            }
                            .show()
                    }
                }

                val fileDrawable = getDrawable(drawables.outline_insert_drive_file_24)
                if (file.isDirectory) {
                    addItem(
                        title = "Clone Repo",
                        description = "Clone a git repo here",
                        icon = getDrawable(drawables.github),
                        listener = {
                            cloneRepo()
                        }
                    )
                    addItem(
                        getString(strings.open_in_terminal),
                        getString(strings.open_dir_in_terminal),
                        getDrawable(drawables.terminal),
                    ) {
                        val intent = Intent(context, Terminal::class.java)
                        intent.putExtra("cwd", file.absolutePath)
                        context.startActivity(intent)
                    }
                    addItem(
                        getString(strings.add_file),
                        getString(strings.add_file_desc),
                        fileDrawable,
                    ) {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "*/*"
                        mainActivity.fileManager?.parentFile = file
                        //mainActivity.startActivityForResult(intent, REQUEST_ADD_FILE)
                        mainActivity.fileManager?.requestAddFile?.launch(intent)
                    }
                    addItem(
                        getString(strings.new_file),
                        getString(strings.create_new_file_desc),
                        fileDrawable,
                    ) {
                        new(createFile = true)
                    }
                    addItem(
                        getString(strings.new_folder),
                        getString(strings.create_new_file_desc),
                        getDrawable(drawables.outline_folder_24),
                    ) {
                        new(createFile = false)
                    }
                    addItem(
                        getString(strings.paste),
                        getString(strings.paste_desc),
                        fileDrawable,
                    ) {
                        if (FileClipboard.isEmpty()) {
                            rkUtils.toast(getString(strings.clipboardempty))
                        } else {
                            LoadingPopup(mainActivity, 350)
                            mainActivity.lifecycleScope.launch(Dispatchers.Default) {
                                if (!FileClipboard.isEmpty()) {
                                    val sourceFile = FileClipboard.getFile()
                                    if (file.isDirectory && sourceFile != null) {
                                        try {
                                            val targetPath = file.toPath().resolve(sourceFile.name)

                                            withContext(Dispatchers.IO) {
                                                if (sourceFile.isDirectory) {
                                                    Files.walk(sourceFile.toPath()).forEach { source ->
                                                        val dest = targetPath.resolve(
                                                            sourceFile.toPath().relativize(source)
                                                        )
                                                        Files.copy(
                                                            source,
                                                            dest,
                                                            StandardCopyOption.REPLACE_EXISTING
                                                        )
                                                    }
                                                } else {
                                                    Files.copy(
                                                        sourceFile.toPath(),
                                                        targetPath,
                                                        StandardCopyOption.REPLACE_EXISTING
                                                    )
                                                }
                                            }

                                            withContext(Dispatchers.Main) {
                                                ProjectManager.currentProject.updateFileAdded(
                                                    mainActivity,
                                                    FileWrapper(targetPath.parent.toFile())
                                                )
                                            }

                                            FileClipboard.clear()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "${getString(strings.failed_move)}: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (file.isFile) {
                    addItem(
                        getString(strings.save_as),
                        getString(strings.save_desc),
                        getDrawable(drawables.save),
                    ) {
                        to_save_file = file
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        startActivityForResult(
                            mainActivity,
                            intent,
                            REQUEST_CODE_OPEN_DIRECTORY,
                            null,
                        )
                    }
                }

                addItem(getString(strings.copy), getString(strings.copy_desc), fileDrawable) {
                    FileClipboard.setFile(file)
                }
            }
            .show()
    }

    private fun new(createFile: Boolean) {
        val popupView: View = LayoutInflater.from(mainActivity).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<EditText>(R.id.name)

        var title = mainActivity.getString(strings.new_folder)
        if (createFile) {
            editText.hint = mainActivity.getString(strings.newFile_hint)
            title = mainActivity.getString(strings.new_file)
        } else {
            editText.hint = mainActivity.getString(strings.dir_example)
        }

        MaterialAlertDialogBuilder(mainActivity)
            .setTitle(title)
            .setView(popupView)
            .setNegativeButton(mainActivity.getString(strings.cancel), null)
            .setPositiveButton(mainActivity.getString(strings.create)) { _: DialogInterface?, _: Int ->
                if (editText.text.toString().isEmpty()) {
                    rkUtils.toast(mainActivity.getString(strings.ask_enter_name))
                    return@setPositiveButton
                }

                val loading = LoadingPopup(mainActivity, null)
                loading.show()

                val fileName = editText.text.toString()
                val newFilePath = file.toPath().resolve(fileName)

                if (Files.exists(newFilePath)) {
                    rkUtils.toast(mainActivity.getString(strings.already_exists))
                    loading.hide()
                    return@setPositiveButton
                }

                mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                    if (createFile) {
                        Files.createFile(newFilePath)
                    } else {
                        Files.createDirectory(newFilePath)
                    }
                    withContext(Dispatchers.Main) {
                        ProjectManager.currentProject.updateFileAdded(mainActivity, FileWrapper(newFilePath.parent.toFile()))
                        loading.hide()
                    }
                }
            }
            .show()
    }

    private fun rename() {
        val popupView: View = LayoutInflater.from(mainActivity).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<EditText>(R.id.name)

        editText.setText(file.name)
        editText.hint = getString(strings.file_name)

        MaterialAlertDialogBuilder(mainActivity)
            .setTitle(mainActivity.getString(strings.rename))
            .setView(popupView)
            .setNegativeButton(mainActivity.getString(strings.cancel), null)
            .setPositiveButton(mainActivity.getString(strings.rename)) { _: DialogInterface?, _: Int ->
                val newFileName = editText.text.toString()

                if (newFileName.isEmpty()) {
                    rkUtils.toast(mainActivity.getString(strings.ask_enter_name))
                    return@setPositiveButton
                }

                val loading = LoadingPopup(mainActivity, null)
                loading.show()

                val targetPath = file.parentFile.toPath().resolve(newFileName)
                if (Files.exists(targetPath)) {
                    rkUtils.toast(mainActivity.getString(strings.already_exists))
                    loading.hide()
                    return@setPositiveButton
                }

                mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        Files.move(
                            file.toPath(),
                            targetPath,
                            StandardCopyOption.ATOMIC_MOVE
                        )

                        withContext(Dispatchers.Main) {
                            ProjectManager.currentProject.updateFileRenamed(
                                mainActivity,
                                FileWrapper(file),
                                targetPath.toFile()
                            )

//                            // Update file references in editor
//                            MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach { f ->
//                                if (f.get()?.type == FragmentType.EDITOR) {
//                                    val editorFragment = f.get()!!.fragment as EditorFragment
//                                    if (editorFragment.file?.getAbsolutePath() == file.absolutePath) {
//                                        editorFragment.file = targetPath.toFile()
//                                    }
//                                }
//                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            rkUtils.toast("${getString(strings.failed_move)}: ${e.message}")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        loading.hide()
                    }
                }
            }
            .show()
    }

    private fun openWith(context: Context, file: File) {
        fun getMimeType(context: Context, file: File): String? {
            val uri: Uri = Uri.fromFile(file)
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            return if (extension != null) {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
            } else {
                context.contentResolver.getType(uri)
            }
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider",
                file,
            )
            val mimeType = getMimeType(context, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, getString(strings.canthandle), Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            rkUtils.toast(getString(strings.file_open_denied))
        }
    }


    private fun cloneRepo() {
        val popupView: View = LayoutInflater.from(mainActivity).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<EditText>(R.id.name)

        var title = "Clone"
        editText.hint = "repo url"

        MaterialAlertDialogBuilder(mainActivity)
            .setTitle(title)
            .setView(popupView)
            .setNegativeButton(mainActivity.getString(strings.cancel), null)
            .setPositiveButton("clone") { _: DialogInterface?, _: Int ->
                if (editText.text.toString().isEmpty()) {
                    "Invalid url".toastIt()
                    return@setPositiveButton
                }

                val loading = LoadingPopup(mainActivity, null)
                loading.show()

                val url = editText.text.toString()

                mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                    GitClient.clone(mainActivity,url,file, onResult = {
                        runOnUiThread{
                            if (it == null){
                                ProjectManager.currentProject.updateFileAdded(mainActivity,FileWrapper(file))
                            }
                            loading.hide()
                            it?.message?.toastIt()
                        }
                    })

                }
            }
            .show()
    }
}