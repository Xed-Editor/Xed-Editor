package com.rk.xededitor.MainActivity.file

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.application
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.FileAction.Companion.to_save_file
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import org.apache.commons.net.io.Util.copyStream
import java.io.File

class FileManager(private val mainActivity: MainActivity) {

    private var requestOpenFile =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(it.data!!.data!!.toPath())
                if (file.exists()) {
                    mainActivity.adapter!!.addFragment(FileWrapper(file))
                } else {
                    mainActivity.adapter!!.addFragment(
                        UriWrapper(it.data!!.data!!)
                    )
                }

            }
        }

    var parentFile: FileObject? = null
    var requestAddFile =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val sourceUri = result.data!!.data!!

                fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
                    var name = "default_file"
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                name = it.getString(nameIndex)
                            }
                        }
                    }
                    return name
                }

                fun copyUriData(contentResolver: ContentResolver, sourceUri: Uri, destinationUri: Uri) {
                    try {
                        contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                                copyStream(inputStream, outputStream)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw RuntimeException("Failed to copy data from $sourceUri to $destinationUri", e)
                    }
                }



                val destinationFile = parentFile!!.createChild(
                    true, getFileName(mainActivity.contentResolver, sourceUri)
                )

                val destinationUri = destinationFile!!.toUri()

                copyUriData(mainActivity.contentResolver,sourceUri,destinationUri)

                parentFile?.let {
                    ProjectManager.currentProject.updateFileAdded(
                        mainActivity, it
                    )
                }
            }
            parentFile = null
        }

    private var requestOpenDir =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(it.data!!.data!!.toPath())
                if (file.exists().not()) {
                    kotlin.runCatching {
                        val takeFlags: Int =
                            (it.data!!.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                        mainActivity.contentResolver.takePersistableUriPermission(
                            it.data!!.data!!, takeFlags
                        )
                    }
                    ProjectManager.addProject(
                        mainActivity, UriWrapper(it.data!!.data!!)
                    )
                } else {
                    ProjectManager.addProject(mainActivity, FileWrapper(file))
                }

            }
        }


    private var requestToSaveFile =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && to_save_file != null) {
                val sourceUri = to_save_file!!.toUri()

                mainActivity.contentResolver.openInputStream(sourceUri)
                    .use { inputStream ->
                        mainActivity.contentResolver.openOutputStream(
                            it.data!!.data!!
                        )?.use { outputStream ->
                            copyStream(inputStream, outputStream)
                        }
                    }
            }
        }

    val createFileLauncher =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val path = data?.data?.toPath()
                val file = File(path.toString())

                val wrapper: FileObject = if (file.exists()) {
                    FileWrapper(file)
                } else {
                    UriWrapper(data!!.data!!)
                }
                mainActivity.adapter?.addFragment(wrapper)
            }
        }


    private var toSaveAsFile: FileObject? = null

    private val directoryPickerLauncher =
        mainActivity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            runCatching {
                uri?.let { selectedUri ->
                    val documentFile = DocumentFile.fromTreeUri(mainActivity, selectedUri)
                    val newFile = documentFile?.createFile("*/*", toSaveAsFile?.getName() ?: "new_file")

                    newFile?.uri?.let { newUri ->
                        mainActivity.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                            toSaveAsFile?.getInputStream()?.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
            }.onFailure {
                rkUtils.toast(it.message)
            }
        }

    fun saveAsFile(file: FileObject) {
        toSaveAsFile = file
        directoryPickerLauncher.launch(null)
    }


    fun requestOpenFile() {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            requestOpenFile.launch(this)
        }
    }

    fun requestOpenDirectory() {
        requestOpenDir.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    fun requestOpenDirectoryToSaveFile() {
        requestToSaveFile.launch(Intent(Intent.ACTION_CREATE_DOCUMENT))
    }

    fun requestOpenFromPath() {
        val popupView = LayoutInflater.from(mainActivity).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<View>(R.id.name) as EditText

        editText.setText("/sdcard")
        editText.hint = getString(strings.ff_path)

        MaterialAlertDialogBuilder(mainActivity).setView(popupView)
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
                    ProjectManager.addProject(mainActivity, FileWrapper(file))
                } else {
                    mainActivity.adapter!!.addFragment(FileWrapper(file))
                }
            }.show()
    }

    companion object {
        private val gits = mutableSetOf<String>()

        suspend fun findGitRoot(file: File): File? {
            gits.forEach { root ->
                if (file.absolutePath.contains(root)) {
                    return File(root)
                }
            }
            var currentFile = file
            while (currentFile.parentFile != null) {
                if (File(currentFile.parentFile, ".git").exists()) {
                    currentFile.parentFile?.let { gits.add(it.absolutePath) }
                    return currentFile.parentFile
                }
                currentFile = currentFile.parentFile!!
            }
            return null
        }
    }
}
