package com.rk.xededitor.MainActivity.treeview2

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.FileClipboard
import com.rk.xededitor.Loading
import com.rk.xededitor.MainActivity.Data
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter.Companion.stopThread
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Timer
import java.util.TimerTask

class HandleFileActions(
    val mContext: MainActivity, rootFolder: DocumentFile, file: DocumentFile, anchorView: View
) {
    companion object {

        @JvmStatic
        val REQUEST_CODE_OPEN_DIRECTORY = 1

        @JvmStatic
        private lateinit var to_save_file: DocumentFile

        @JvmStatic
        fun saveFile(ctx: MainActivity, destination: Uri) {
            val loading = Loading(ctx, null).show()
            Thread {
                fun copyDocumentFile(source: DocumentFile, target: DocumentFile): Boolean? {
                    fun copyStream(input: InputStream, output: OutputStream) {
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                    return try {
                        ctx.contentResolver.openInputStream(source.uri)?.use { inputStream ->
                            ctx.contentResolver.openOutputStream(target.uri)?.use { outputStream ->
                                copyStream(inputStream, outputStream)
                                true
                            }
                        } ?: false
                    } catch (e: IOException) {
                        e.printStackTrace()
                        false
                    }
                }

                val pickedDir = DocumentFile.fromTreeUri(ctx, destination)

                if (pickedDir != null && pickedDir.canWrite()) {
                    val newFile = pickedDir.createFile(
                        to_save_file.type ?: "application/octet-stream", to_save_file.name!!
                    )
                    copyDocumentFile(to_save_file, newFile!!)
                }
                ctx.runOnUiThread {
                    MA(ctx, Data.rootFolder)
                }
                loading.hide()
            }.start()

        }
    }


    init {
        val popupMenu = PopupMenu(mContext, anchorView)
        val inflater = popupMenu.menuInflater
        inflater.inflate(R.menu.root_file_options, popupMenu.menu)

        if (file == rootFolder) {
            popupMenu.menu.findItem(R.id.reselect).setVisible(true)
            popupMenu.menu.findItem(R.id.close).setVisible(true)
            popupMenu.menu.findItem(R.id.openFile).setVisible(true)

            popupMenu.menu.findItem(R.id.ceateFolder).setVisible(true)
            popupMenu.menu.findItem(R.id.createFile).setVisible(true)
            popupMenu.menu.findItem(R.id.paste).setVisible(true)

            popupMenu.menu.findItem(R.id.paste).setEnabled(!FileClipboard.isEmpty())

        } else if (file.isDirectory) {

            popupMenu.menu.findItem(R.id.ceateFolder).setVisible(true)
            popupMenu.menu.findItem(R.id.createFile).setVisible(true)
            var copy = popupMenu.menu.findItem(R.id.copy)
            copy.setVisible(true)
            if (!FileClipboard.isEmpty()){
                copy.setTitle("Copy (Override)")
            }
            popupMenu.menu.findItem(R.id.paste).setVisible(true)

            popupMenu.menu.findItem(R.id.paste).setEnabled(!FileClipboard.isEmpty())

        } else {
            popupMenu.menu.findItem(R.id.save_as).setVisible(true)
            var copy = popupMenu.menu.findItem(R.id.copy)
            copy.setVisible(true)
            if (!FileClipboard.isEmpty()){
                copy.setTitle("Copy (Override)")
            }
        }

        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            val id = item.itemId

            if (id == R.id.save_as) {
                to_save_file = file
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(mContext, intent, REQUEST_CODE_OPEN_DIRECTORY, null)
            } else if (id == R.id.reselect) {
                mContext.reselctDir(null)
            } else if (id == R.id.openFile) {
                mContext.openFile(null)
                stopThread()
            } else if (id == R.id.close) {
                if (file == rootFolder) {
                    mContext.binding.mainView.visibility = View.GONE
                    mContext.binding.safbuttons.visibility = View.VISIBLE
                    mContext.binding.maindrawer.visibility = View.GONE
                    mContext.binding.drawerToolbar.visibility = View.GONE
                    stopThread()
                    val uriString = SettingsData.getSetting(mContext, "lastOpenedUri", "null")
                    if (uriString != "null") {
                        val uri = Uri.parse(uriString)
                        if (mContext.hasUriPermission(uri)) {
                            mContext.revokeUriPermission(uri)
                        }
                        SettingsData.setSetting(mContext, "lastOpenedUri", "null")
                    }
                }

            } else if (id == R.id.ceateFolder) {
                val popupView: View =
                    LayoutInflater.from(mContext).inflate(R.layout.popup_new, null)
                val editText = popupView.findViewById<EditText>(R.id.name)
                editText.hint = "Name eg. Test"
                MaterialAlertDialogBuilder(mContext).setTitle("New Folder").setView(popupView)
                    .setNegativeButton("Cancel", null).setPositiveButton(
                        "Create"
                    ) { _: DialogInterface?, _: Int ->
                        if (editText.getText().toString().isEmpty()) {
                            rkUtils.toast("please enter name")
                            return@setPositiveButton
                        }
                        mContext.hideKeyboard()
                        val inflater1: LayoutInflater = mContext.layoutInflater
                        val dialogView: View = inflater1.inflate(R.layout.progress_dialog, null)
                        val dialog = MaterialAlertDialogBuilder(mContext).setView(dialogView)
                            .setCancelable(false).show()
                        val timer = Timer()
                        val timerTask: TimerTask = object : TimerTask() {
                            override fun run() {
                                mContext.runOnUiThread {
                                    val fileName = editText.getText().toString()
                                    for (xfile in file.listFiles()) {
                                        if (xfile.name == fileName) {
                                            rkUtils.toast("Error : a file/folder already exist with this name")
                                            return@runOnUiThread
                                        }
                                    }
                                    file.createDirectory(fileName)
                                    MA(
                                        mContext, rootFolder
                                    )
                                }
                                dialog.dismiss()
                                timer.cancel()
                            }
                        }
                        timer.schedule(timerTask, 0, 800)
                    }.show()
            } else if (id == R.id.createFile) {
                val popupView: View =
                    LayoutInflater.from(mContext).inflate(R.layout.popup_new, null)
                val editText = popupView.findViewById<EditText>(R.id.name)
                val editText1 = popupView.findViewById<EditText>(R.id.mime)
                popupView.findViewById<View>(R.id.mimeTypeEditor).visibility = View.VISIBLE
                val mimeType: String
                val mimeTypeU = editText1.getText().toString()
                mimeType = if (mimeTypeU.isEmpty()) {
                    "text/plain"
                } else {
                    mimeTypeU
                }
                MaterialAlertDialogBuilder(mContext).setTitle("New File").setView(popupView)
                    .setNegativeButton("Cancel", null).setPositiveButton(
                        "Create"
                    ) { _: DialogInterface?, _: Int ->
                        if (editText.getText().toString().isEmpty()) {
                            rkUtils.toast("please enter name")
                            return@setPositiveButton
                        }
                        mContext.hideKeyboard()
                        val loading = Loading(mContext, null).show()
                        val timer = Timer()
                        val timerTask: TimerTask = object : TimerTask() {
                            override fun run() {
                                mContext.runOnUiThread {
                                    val fileName = editText.getText().toString()
                                    for (xfile in file.listFiles()) {
                                        if (xfile.name == fileName) {
                                            rkUtils.toast("Error : a file/folder already exist with this name")
                                            return@runOnUiThread
                                        }
                                    }
                                    val child = file.createFile(mimeType, "file")
                                    child!!.renameTo(fileName)
                                    MA(
                                        mContext, rootFolder
                                    )
                                }
                                loading.hide()
                                timer.cancel()
                            }
                        }
                        timer.schedule(timerTask, 0, 800)
                    }.show()
            } else if (id == R.id.delete) {
                MaterialAlertDialogBuilder(mContext).setTitle("Attention")
                    .setMessage("Are you sure you want to delete " + file.name).setNegativeButton(
                        "Delete"
                    ) { _: DialogInterface?, _: Int ->

                        val loading = Loading(mContext, null).show()
                        Thread {
                            //delete file
                            if (file != rootFolder) {
                                file.delete()
                            }

                            mContext.runOnUiThread {

                                if (file == rootFolder) {
                                    rootFolder.delete()
                                    Data.activity.onOptionsItemSelected(Data.menu.findItem(R.id.action_all))
                                    if (Data.activity.adapter != null) {
                                        Data.activity.adapter.clear()
                                    }

                                    if (Data.mTabLayout.tabCount < 1) {
                                        Data.activity.binding.tabs.visibility = View.GONE
                                        Data.activity.binding.mainView.visibility = View.GONE
                                        Data.activity.binding.openBtn.visibility = View.VISIBLE
                                    }
                                    val visible =
                                        !(Data.fragments == null || Data.fragments.isEmpty())
                                    Data.menu.findItem(R.id.search).setVisible(visible)
                                    Data.menu.findItem(R.id.action_save).setVisible(visible)
                                    Data.menu.findItem(R.id.action_all).setVisible(visible)
                                    Data.menu.findItem(R.id.batchrep).setVisible(visible)

                                    mContext.binding.mainView.visibility = View.GONE
                                    mContext.binding.safbuttons.visibility = View.VISIBLE
                                    mContext.binding.maindrawer.visibility = View.GONE
                                    mContext.binding.drawerToolbar.visibility = View.GONE
                                    stopThread()
                                    val uriString = SettingsData.getSetting(
                                        mContext, "lastOpenedUri", "null"
                                    )
                                    if (uriString != "null") {
                                        val uri = Uri.parse(uriString)
                                        if (mContext.hasUriPermission(uri)) {
                                            mContext.revokeUriPermission(uri)
                                        }
                                        SettingsData.setSetting(
                                            mContext, "lastOpenedUri", "null"
                                        )
                                    }
                                    rkUtils.toast("Please reselect the directory")
                                }
                                //recreate the tree
                                MA(mContext, rootFolder)

                                loading.hide()
                            }
                        }.start()
                    }.setPositiveButton("Cancel", null).show()
            } else if (id == R.id.paste) {
                val fileToPaste = FileClipboard.getFile()
                if (fileToPaste != null) {
                    if (file.isDirectory) {
                        copyDocumentFile(mContext,fileToPaste.uri,file)
                        MA(mContext,rootFolder)
                    }
                } else {
                    rkUtils.toast("Clipboard is empty")
                }
            } else if (id == R.id.copy) {
                FileClipboard.setFile(file)
            } else if (id == R.id.rename) {
                val popupView: View =
                    LayoutInflater.from(mContext).inflate(R.layout.popup_new, null)
                val editText = popupView.findViewById<EditText>(R.id.name)
                editText.setText(file.name)
                if (file.isDirectory) {
                    editText.hint = "Name eg. Test"
                }
                MaterialAlertDialogBuilder(mContext).setTitle("Rename").setView(popupView)
                    .setNegativeButton("Cancel", null).setPositiveButton(
                        "Rename"
                    ) { _: DialogInterface?, _: Int ->
                        if (editText.getText().toString().isEmpty()) {
                            rkUtils.toast("please enter name")
                            return@setPositiveButton
                        }
                        mContext.hideKeyboard()
                        val loading = Loading(mContext, null).show()
                        val timer = Timer()
                        val timerTask: TimerTask = object : TimerTask() {
                            override fun run() {
                                mContext.runOnUiThread {
                                    val fileName = editText.getText().toString()
                                    for (xfile in file.listFiles()) {
                                        if (xfile.name == fileName) {
                                            rkUtils.toast("Error : a file/folder already exist with this name")
                                            return@runOnUiThread
                                        }
                                    }
                                    file.renameTo(fileName)

                                    if (file == rootFolder) {

                                        Data.activity.onOptionsItemSelected(Data.menu.findItem(R.id.action_all))
                                        if (Data.activity.adapter != null) {
                                            Data.activity.adapter.clear()
                                        }

                                        if (Data.mTabLayout.tabCount < 1) {
                                            Data.activity.binding.tabs.visibility = View.GONE
                                            Data.activity.binding.mainView.visibility = View.GONE
                                            Data.activity.binding.openBtn.visibility = View.VISIBLE
                                        }
                                        val visible =
                                            !(Data.fragments == null || Data.fragments.isEmpty())
                                        Data.menu.findItem(R.id.search).setVisible(visible)
                                        Data.menu.findItem(R.id.action_save).setVisible(visible)
                                        Data.menu.findItem(R.id.action_all).setVisible(visible)
                                        Data.menu.findItem(R.id.batchrep).setVisible(visible)

                                        mContext.binding.mainView.visibility = View.GONE
                                        mContext.binding.safbuttons.visibility = View.VISIBLE
                                        mContext.binding.maindrawer.visibility = View.GONE
                                        mContext.binding.drawerToolbar.visibility = View.GONE
                                        stopThread()
                                        val uriString = SettingsData.getSetting(
                                            mContext, "lastOpenedUri", "null"
                                        )
                                        if (uriString != "null") {
                                            val uri = Uri.parse(uriString)
                                            if (mContext.hasUriPermission(uri)) {
                                                mContext.revokeUriPermission(uri)
                                            }
                                            SettingsData.setSetting(
                                                mContext, "lastOpenedUri", "null"
                                            )
                                        }
                                        rkUtils.toast("Please reselect the directory")
                                    } else {
                                        //recreate the tree
                                        MA(mContext, rootFolder)
                                    }


                                }
                                loading.hide()
                                timer.cancel()
                            }
                        }
                        timer.schedule(timerTask, 0, 200)
                    }.show()
            } else if (id == R.id.openWith) {
                //open file with other apps
                val uri = file.uri
                val mimeType = rkUtils.getMimeType(mContext, file)
                println(mimeType)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

                // Check if there's an app to handle this intent
                if (intent.resolveActivity(mContext.packageManager) != null) {
                    mContext.startActivity(intent)
                } else {
                    rkUtils.toast("No app found to handle the file")
                }
            }
            true
        }
        popupMenu.show()
    }


    private fun copyDocumentFile(context: Context, sourceUri: Uri, destinationDir: DocumentFile) {
        val loading = Loading(mContext,null).show()
        Thread{
            val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)
            if (sourceFile == null || !sourceFile.exists()) {
                return@Thread
            }

            val fileName = sourceFile.name ?: return@Thread
            val destinationFile =
                destinationDir.createFile(sourceFile.type ?: "application/octet-stream", fileName)
                    ?: return@Thread

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(sourceUri)
                outputStream = context.contentResolver.openOutputStream(destinationFile.uri)
                if (inputStream != null && outputStream != null) {
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    inputStream.close()
                    outputStream.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                inputStream?.close()
                outputStream?.close()
                loading.hide()
            }

        }.start()

    }

}
