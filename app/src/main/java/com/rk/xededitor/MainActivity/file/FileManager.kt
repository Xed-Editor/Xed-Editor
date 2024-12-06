package com.rk.xededitor.MainActivity.file

import android.app.Activity
import android.content.Intent
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.libcommons.PathUtils.toPath
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import java.io.File
import java.net.URLConnection

class FileManager(private val mainActivity: MainActivity) {

    private var requestOpenFile =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(it.data!!.data!!.toPath())
                mainActivity.adapter!!.addFragment(file)
            }
        }

    private var requestOpenDir =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(it.data!!.data!!.toPath())
                ProjectManager.addProject(mainActivity, file)
            }
        }
    
    val createFileLauncher = mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val path = data?.data?.toPath()
            val file = File(path.toString())
            if (file.exists() and file.isFile){
                mainActivity.adapter!!.addFragment(file)
            }else{
                rkUtils.toast("Unsupported file location ${data?.data}")
            }
        }
    }


    private var toSaveAsFile: File? = null

    private val directoryPickerLauncher = mainActivity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { uri ->
            toSaveAsFile?.let { file ->
                val resolver = mainActivity.contentResolver
                val mimeType = URLConnection.guessContentTypeFromName(file.name)
                val documentUri = DocumentsContract.createDocument(
                    resolver,
                    uri,
                    mimeType ?: "application/octet-stream",
                    file.name
                )

                documentUri?.let { docUri ->
                    resolver.openOutputStream(docUri)?.use { outputStream ->
                        outputStream.write(file.readBytes())
                    }
                }
            }
        }
    }

    fun saveAsFile(file: File) {
        rkUtils.toast(strings.ni.getString())
        return
        toSaveAsFile = file
        directoryPickerLauncher.launch(null)
    }



    fun requestOpenFile() {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType("*/*")
            requestOpenFile.launch(this)
        }
    }

    fun requestOpenDirectory() {
        requestOpenDir.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    fun requestOpenFromPath() {
        val popupView = LayoutInflater.from(mainActivity).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<View>(R.id.name) as EditText

        editText.setText("/sdcard")
        editText.hint = getString(strings.ff_path)

        MaterialAlertDialogBuilder(mainActivity)
            .setView(popupView)
            .setTitle(getString(strings.path))
            .setNegativeButton(mainActivity.getString(strings.cancel), null)
            .setPositiveButton(getString(strings.open)) { _, _ ->
                val path = editText.text.toString()
                val file = File(path)

                if (path.isEmpty()) {
                    rkUtils.toast(getString(strings.enter_path))
                    return@setPositiveButton
                }

                if (!file.exists()) {
                    rkUtils.toast(getString(strings.invalid_path))
                    return@setPositiveButton
                }

                if (!file.canRead() || !file.canWrite()) {
                    rkUtils.toast(getString(strings.permission_denied))
                    return@setPositiveButton
                }

                if (file.isDirectory) {
                    ProjectManager.addProject(mainActivity, file)
                } else {
                    mainActivity.adapter!!.addFragment(file)
                }
            }
            .show()
    }

    companion object {
        fun findGitRoot(file: File?): File? {
            var currentFile = file
            while (currentFile?.parentFile != null) {
                if (File(currentFile.parentFile, ".git").exists()) {
                    return currentFile.parentFile
                }
                currentFile = currentFile.parentFile
            }
            return null
        }
    }
}
