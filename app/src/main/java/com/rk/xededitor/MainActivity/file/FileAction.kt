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
import com.jaredrummler.ktsh.Shell
import com.rk.libcommons.ActionPopup
import com.rk.libcommons.LoadingPopup
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import com.rk.xededitor.terminal.Terminal
import java.io.File
import java.util.Locale
import kotlin.random.Random
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
                        ProjectManager.removeProject(mainActivity, rootFolder)
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
                            .setTitle(context.getString(strings.delete))
                            .setMessage(context.getString(strings.ask_del) + " ${file.name} ")
                            .setNegativeButton(context.getString(strings.cancel), null)
                            .setPositiveButton(context.getString(strings.delete)) {
                                _: DialogInterface?,
                                _: Int ->
                                val loading = LoadingPopup(mainActivity, null).show()
                                ProjectManager.currentProject.updateFileDeleted(mainActivity, file)
                                mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                                    if (file.isFile) {
                                        file.delete()
                                    } else {
                                        file.deleteRecursively()
                                    }
                                    withContext(Dispatchers.Main) { loading.hide() }
                                }
                            }
                            .show()
                    }
                }

                val fileDrawable = getDrawable(drawables.outline_insert_drive_file_24)
                if (file.isDirectory) {
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
                        intent.setType("*/*")
                        mainActivity.startActivityForResult(intent, REQUEST_ADD_FILE)
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
                            mainActivity.let {
                                it.lifecycleScope.launch(Dispatchers.Default) {
                                    if (!FileClipboard.isEmpty()) {
                                        val sourceFile = FileClipboard.getFile()
                                        if (file.isDirectory && sourceFile != null) {
                                            try {
                                                val targetPath =
                                                    file.toPath().resolve(sourceFile.name)

                                                // Move the source file to the target directory
                                                withContext(Dispatchers.IO) {
                                                    Shell.SH.apply {
                                                        run(
                                                            "cp -r ${sourceFile.absolutePath} $targetPath"
                                                        )
                                                        shutdown()
                                                    }
                                                }

                                                // Update the TreeView to reflect the changes
                                                withContext(Dispatchers.Main) {
                                                    //
                                                    // adapter?.newFile(file)
                                                    //                                        if
                                                    // (file == rootFolder) {
                                                    //
                                                    // TreeView(context, rootFolder)
                                                    //                                        }
                                                    // BaseActivity.getActivity(MainActivity::class.java)?.fileTree?.loadFiles(file(rootFolder))
                                                    ProjectManager.currentProject.updateFileAdded(
                                                        mainActivity,
                                                        targetPath.toFile(),
                                                    )
                                                }

                                                // Optionally, clear the clipboard after pasting
                                                FileClipboard.clear()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                            context,
                                                            "${getString(strings.failed_move)}: ${e.message}",
                                                            Toast.LENGTH_SHORT,
                                                        )
                                                        .show()
                                                }
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
            .setPositiveButton(mainActivity.getString(strings.create)) {
                _: DialogInterface?,
                _: Int ->
                if (editText.getText().toString().isEmpty()) {
                    rkUtils.toast(mainActivity.getString(strings.ask_enter_name))
                    return@setPositiveButton
                }

                val loading = LoadingPopup(mainActivity, null)

                loading.show()
                val fileName = editText.getText().toString()
                for (xfile in file.listFiles()!!) {
                    if (xfile.name == fileName) {
                        rkUtils.toast(mainActivity.getString(strings.already_exists))
                        loading.hide()
                        return@setPositiveButton
                    }
                }

                if (createFile) {
                    File(file, fileName).createNewFile()
                } else {
                    File(file, fileName).mkdir()
                }

                ProjectManager.currentProject.updateFileAdded(mainActivity, file)

                loading.hide()
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
            .setPositiveButton(mainActivity.getString(strings.rename)) {
                _: DialogInterface?,
                _: Int ->
                val newFileName = editText.text.toString()

                if (newFileName.isEmpty()) {
                    rkUtils.toast(mainActivity.getString(strings.ask_enter_name))
                    return@setPositiveButton
                }

                val loading = LoadingPopup(mainActivity, null)
                loading.show()

                // Check if the new file name already exists
                val parentDir = file.parentFile
                val fileExists =
                    parentDir?.list()?.any { it.equals(newFileName, ignoreCase = false) } == true

                if (fileExists) {
                    rkUtils.toast(mainActivity.getString(strings.already_exists))
                    loading.hide()
                    return@setPositiveButton
                }
                
                fun rename(file: File, to: String) {
                    mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                        val random = Random(28958510971)
                        val xf = (random.nextInt() + random.nextInt()).toString()
                        Shell.SH.apply {
                            // Quote the file paths to handle spaces properly
                            run("mv \"${file.canonicalPath}\" \"${file.parentFile}/$xf\"")
                            run("mv \"${file.parentFile}/$xf\" \"${file.parentFile}/$to\"")
                            shutdown()
                            withContext(Dispatchers.Main) {
                                ProjectManager.currentProject.updateFileRenamed(
                                    mainActivity,
                                    file,
                                    File(file.parentFile, to),
                                )
                            }
                            // Update file when renaming
                            MainActivity.activityRef
                                .get()
                                ?.adapter
                                ?.tabFragments
                                ?.values
                                ?.forEach { f ->
                                    if (f.get()?.type == FragmentType.EDITOR){
                                        val editorFragment = f.get()!!.fragment as EditorFragment
                                        if (editorFragment.file?.absolutePath == file.absolutePath) {
                                            editorFragment.file = File(to)
                                            // TODO: Update tab text too
                                        }
                                    }
                                    
                                }
                        }
                    }
                }
                
                
                rename(file, newFileName)

                loading.hide()
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
            val uri: Uri =
                FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".fileprovider",
                    file,
                )
            val mimeType = getMimeType(context, file)

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }

            // Check if there's an app to handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, context.getString(strings.canthandle), Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            rkUtils.toast(getString(strings.file_open_denied))
        }
    }
}
