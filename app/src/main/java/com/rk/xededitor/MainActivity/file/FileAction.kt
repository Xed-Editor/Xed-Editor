package com.rk.xededitor.MainActivity.file

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.filetree.provider.file
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler.updateMenuItems
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

class FileAction(
    private val context: MainActivity,
    private val rootFolder: File,
    private val file: File,
) {

    companion object {
        const val REQUEST_CODE_OPEN_DIRECTORY = 17618
        const val REQUEST_ADD_FILE = 824689
        var to_save_file: File? = null
        var Staticfile: File? = null
    }

    private var popupView: View

    init {
        Staticfile = file
        val inflater = context.layoutInflater
        popupView = inflater.inflate(R.layout.file_action, null)
        handleOptionsVisibility()
        val popup =
            MaterialAlertDialogBuilder(context).setView(popupView).setTitle("Actions").show()


        val root = popupView.findViewById<LinearLayout>(R.id.root)
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            child.setOnClickListener {
                popup.dismiss()
                handleMenuItemClick(child.id)

            }
        }


    }


    private fun handleOptionsVisibility() {
        val openWith = popupView.findViewById<LinearLayout>(R.id.openWith)
        val saveas = popupView.findViewById<LinearLayout>(R.id.save_as)
        val close = popupView.findViewById<LinearLayout>(R.id.close)
        val delete = popupView.findViewById<LinearLayout>(R.id.delete)
        val refresh = popupView.findViewById<LinearLayout>(R.id.refresh)
        val reselect = popupView.findViewById<LinearLayout>(R.id.reselect)
        val openFile = popupView.findViewById<LinearLayout>(R.id.openFile)
        val newfile = popupView.findViewById<LinearLayout>(R.id.createFile)
        val newfolder = popupView.findViewById<LinearLayout>(R.id.createFolder)
        val paste = popupView.findViewById<LinearLayout>(R.id.paste)
        val copy = popupView.findViewById<LinearLayout>(R.id.copy)
        val rename = popupView.findViewById<LinearLayout>(R.id.rename)
        val addFile = popupView.findViewById<LinearLayout>(R.id.addFile)


        if (file.isDirectory) {
            saveas.visibility = View.GONE
        } else {
            newfile.visibility = View.GONE
            newfolder.visibility = View.GONE
            paste.visibility = View.GONE
            addFile.visibility = View.GONE
        }

        if (file == rootFolder) {
            openWith.visibility = View.GONE
            delete.visibility = View.GONE
            copy.visibility = View.GONE
            rename.visibility = View.GONE
        } else {
            close.visibility = View.GONE
            refresh.visibility = View.GONE
            reselect.visibility = View.GONE
            openFile.visibility = View.GONE
        }


    }


    private fun handleMenuItemClick(id: Int): Boolean {
        return when (id) {

            R.id.addFile -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.setType("*/*")

                context.startActivityForResult(intent, REQUEST_ADD_FILE)
                return true
            }

            R.id.refresh -> {
                // Handle refresh action
                BaseActivity.getActivity(MainActivity::class.java)?.fileTree?.loadFiles(file(rootFolder))
                true
            }

            R.id.reselect -> {
                // Handle reselect action
                context.reselectDir(null)
                true
            }

            R.id.openFile -> {
                // Handle openFile action
                context.openFile(null)
                true
            }

            R.id.createFolder -> {
                // Handle createFolder action
                new(false)

                true
            }

            R.id.createFile -> {
                // Handle createFile action
                new(true)
                true
            }

            R.id.rename -> {
                // Handle rename action
                rename()
                true
            }

            R.id.openWith -> {
                // Handle openWith action
                openWith(context, file)
                true
            }

            R.id.save_as -> {
                // Handle save_as action
                to_save_file = file
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(
                    context,
                    intent,
                    REQUEST_CODE_OPEN_DIRECTORY,
                    null
                )
                true
            }

            R.id.copy -> {
                // Handle copy action
                FileClipboard.setFile(file)
                true
            }

            R.id.paste -> {
                // Handle paste action

                if (FileClipboard.isEmpty()) {
                    rkUtils.toast(context, "File Clipboard is empty")
                    return false
                }

                LoadingPopup(context, 350)
                BaseActivity.getActivity(MainActivity::class.java)?.let {
                    it.lifecycleScope.launch(Dispatchers.Default) {
                        if (!FileClipboard.isEmpty()) {
                            val sourceFile = FileClipboard.getFile()
                            if (file.isDirectory && sourceFile != null) {
                                try {
                                    val targetPath = file.toPath().resolve(sourceFile.name)

                                    // Move the source file to the target directory
                                    withContext(Dispatchers.IO) {
                                        Files.copy(
                                            sourceFile.toPath(),
                                            targetPath,
                                            StandardCopyOption.REPLACE_EXISTING
                                        )
                                    }


                                    // Update the TreeView to reflect the changes
                                    withContext(Dispatchers.Main) {
//                                        adapter?.newFile(file)
//                                        if (file == rootFolder) {
//                                            TreeView(context, rootFolder)
//                                        }
                                        BaseActivity.getActivity(MainActivity::class.java)?.fileTree?.loadFiles(file(rootFolder))

                                    }

                                    // Optionally, clear the clipboard after pasting
                                    FileClipboard.clear()

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Failed to move file: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                }
                            }
                        }
                    }
                }

                true
            }


            R.id.delete -> {
                // Handle delete action

                MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.delete))
                    .setMessage(context.getString(R.string.ask_del) + " this file?")
                    .setNegativeButton(context.getString(R.string.cancel), null).setPositiveButton(
                        context.getString(R.string.delete)
                    ) { _: DialogInterface?, _: Int ->


                        if (file == rootFolder) {
                            context.binding.mainView.visibility = View.GONE
                            context.binding.maindrawer.visibility = View.GONE
                            context.adapter?.clear()

                        } else {
                            if (file.isFile) {

                                file.delete()
                            } else {

                                file.deleteRecursively()
                            }

                            // TreeView(context, rootFolder)
                            //adapter?.removeFile(file)
                            BaseActivity.getActivity(MainActivity::class.java)?.fileTree?.loadFiles(file(rootFolder))

                        }


                    }.show()







                true
            }

            R.id.close -> {
                // Handle close action
                SettingsData.setString(Keys.LAST_OPENED_PATH, null)
                close()

                true
            }

            else -> false
        }
    }


    private fun getMimeType(context: Context, file: File): String? {
        val uri: Uri = Uri.fromFile(file)
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return if (extension != null) {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
        } else {
            context.contentResolver.getType(uri)
        }
    }

    private fun openWith(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context, context.applicationContext.packageName + ".fileprovider", file
            )
            val mimeType = getMimeType(context, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
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
                Toast.makeText(context, context.getString(R.string.canthandle), Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            rkUtils.toast(context, "opening this file with other apps is not permitted")
        }
    }

    private fun close() {
        context.adapter?.clear()
        for (i in 0 until mTabLayout.tabCount) {
            mTabLayout.getTabAt(i)?.apply {
                text = fragments[i].fileName
            }
        }
        if (mTabLayout.tabCount < 1) {
            context.binding.tabs.visibility = View.GONE
            context.binding.mainView.visibility = View.GONE
            context.binding.openBtn.visibility = View.VISIBLE
        }
        updateMenuItems()
        context.binding.maindrawer.visibility = View.GONE
        ProjectManager.removeProject(rootFolder)
    }

    private fun new(createFile: Boolean) {
        val popupView: View = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<EditText>(R.id.name)

        var title = context.getString(R.string.new_folder)
        if (createFile) {
            editText.hint = context.getString(R.string.newFile_hint)
            title = context.getString(R.string.new_file)
        } else {
            editText.hint = context.getString(R.string.dir_example)
        }

        MaterialAlertDialogBuilder(context).setTitle(title).setView(popupView)
            .setNegativeButton(context.getString(R.string.cancel), null).setPositiveButton(
                context.getString(R.string.create)
            ) { _: DialogInterface?, _: Int ->
                if (editText.getText().toString().isEmpty()) {
                    rkUtils.toast(context, context.getString(R.string.ask_enter_name))
                    return@setPositiveButton
                }

                val loading = LoadingPopup(context, null)

                loading.show()
                val fileName = editText.getText().toString()
                for (xfile in file.listFiles()!!) {
                    if (xfile.name == fileName) {
                        rkUtils.toast(context, context.getString(R.string.already_exists))
                        loading.hide()
                        return@setPositiveButton
                    }
                }

                if (createFile) {
                    File(file, fileName).createNewFile()
                } else {
                    File(file, fileName).mkdir()
                }

                //TreeView(context, rootFolder)
//                adapter?.newFile(file)
//                if (file == rootFolder) {
//                    TreeView(context, rootFolder)
//                }
                BaseActivity.getActivity(MainActivity::class.java)?.fileTree?.loadFiles(file(rootFolder))

                loading.hide()
            }.show()
    }

    private fun rename() {
        val popupView: View = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<EditText>(R.id.name)

        editText.setText(file.name)
        editText.hint = "file name"

        MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.rename))
            .setView(popupView).setNegativeButton(context.getString(R.string.cancel), null)
            .setPositiveButton(
                context.getString(R.string.rename)
            ) { _: DialogInterface?, _: Int ->
                if (editText.getText().toString().isEmpty()) {
                    rkUtils.toast(context, context.getString(R.string.ask_enter_name))
                    return@setPositiveButton
                }

                val loading = LoadingPopup(context, null)

                loading.show()
                val fileName = editText.getText().toString()
                for (xfile in file.parentFile?.listFiles()!!) {
                    if (xfile.name == fileName) {
                        rkUtils.toast(context, context.getString(R.string.already_exists))
                        loading.hide()
                        return@setPositiveButton
                    }
                }

                file.renameTo(File(file.parentFile, fileName))

                //TreeView(context, rootFolder)
//                adapter?.renameFile(file, File(file.parentFile, fileName))
//                if (file == rootFolder) {
//                    TreeView(context, rootFolder)
//                }
                BaseActivity.getActivity(MainActivity::class.java)?.fileTree?.loadFiles(file(rootFolder))

                loading.hide()
            }.show()
    }

}
