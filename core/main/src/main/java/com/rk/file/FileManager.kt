package com.rk.file

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.rk.compose.filetree.addProject
import com.rk.compose.filetree.fileTreeViewModel
import com.rk.DefaultScope
import com.rk.libcommons.application
import com.rk.libcommons.askInput
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util.copyStream
import java.io.File
import androidx.activity.ComponentActivity
import com.rk.xededitor.ui.activities.main.MainActivity

var to_save_file: FileObject? = null
class FileManager(private val activity: ComponentActivity) {

    private fun getString(@StringRes id: Int): String = id.getString()

    // Generic activity result handler
    private val activityResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activityResultCallback?.invoke(result)
        activityResultCallback = null
    }
    private var activityResultCallback: ((ActivityResult) -> Unit)? = null

    // Generic directory picker
    private val directoryPickerLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        directoryPickerCallback?.invoke(uri)
        directoryPickerCallback = null
    }
    private var directoryPickerCallback: ((Uri?) -> Unit)? = null

    private fun launchActivityForResult(intent: Intent, callback: (ActivityResult) -> Unit) {
        activityResultCallback = callback
        activityResultLauncher.launch(intent)
    }

    private fun launchDirectoryPicker(callback: (Uri?) -> Unit) {
        directoryPickerCallback = callback
        directoryPickerLauncher.launch(null)
    }

    fun requestOpenFile(mimeType: String = "*/*", callback: (Uri?) -> Unit) {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            launchActivityForResult(this) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    callback(result.data?.data)
                } else {
                    callback(null)
                }
            }
        }
    }

    fun requestOpenDirectory(callback: (Uri?) -> Unit) {
        launchDirectoryPicker { uri ->
            uri?.let {
                runCatching {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    activity.contentResolver.takePersistableUriPermission(it, takeFlags)
                }.onFailure { e -> e.printStackTrace() }
            }
            callback(uri)
        }
    }




    fun createNewFile(mimeType:String, title: String, callback: (FileObject?) -> Unit = {}) {
        launchActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, title)
        }) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data

                val fileObject = uri?.toFileObject(isFile = true)
                callback(fileObject)
            } else {
                callback(null)
            }
        }
    }


    var parentFile: FileObject? = null
    fun requestAddFile(parent: FileObject, callback: (FileObject?) -> Unit = {}) {
        parentFile = parent
        launchActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                callback(null)
                parentFile = null
                return@launchActivityForResult
            }

            val sourceUri = result.data?.data ?: run {
                callback(null)
                parentFile = null
                return@launchActivityForResult
            }

            DefaultScope.launch(Dispatchers.IO) {
                try {
                    val fileName = getFileName(activity.contentResolver, sourceUri)
                    val destinationFile = parentFile?.createChild(true, fileName)

                    destinationFile?.let { file ->
                        copyUriData(activity.contentResolver, sourceUri, file.toUri())
                        withContext(Dispatchers.Main) {
                            fileTreeViewModel?.updateCache(parentFile!!)
                            callback(file)
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            callback(null)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                } finally {
                    parentFile = null
                }
            }
        }
    }

    fun selectDirForNewFileLaunch(fileName: String, callback: (FileObject?) -> Unit = {}) {
        launchDirectoryPicker { uri ->
            if (uri == null) {
                callback(null)
                return@launchDirectoryPicker
            }

            try {
                val fileObject = uri.toFileObject(isFile = true)
                if (fileObject.hasChild(fileName)) {
                    toast("File with name $fileName already exists")
                    callback(null)
                } else {
                    val newFile = fileObject.createChild(true, fileName)
                    callback(newFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        }
    }

    fun requestOpenDirectoryToSaveFile(file: FileObject, callback: (Boolean) -> Unit = {}) {
        to_save_file = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        val activities = application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        if (activities.isNotEmpty()) {
            launchActivityForResult(intent) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        DefaultScope.launch(Dispatchers.IO) {
                            try {
                                activity.contentResolver.openInputStream(file.toUri())
                                    .use { inputStream ->
                                        activity.contentResolver.openOutputStream(uri)
                                            ?.use { outputStream ->
                                                inputStream?.copyTo(outputStream)
                                                callback(true)
                                            } ?: callback(false)
                                    }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                callback(false)
                            }
                        }
                    } else {
                        callback(false)
                    }
                } else {
                    callback(false)
                }
            }
        } else {
            launchDirectoryPicker { uri ->
                if (uri == null) {
                    callback(false)
                    return@launchDirectoryPicker
                }

                DefaultScope.launch(Dispatchers.IO) {
                    try {
                        activity.contentResolver.openInputStream(file.toUri())
                            .use { inputStream ->
                                activity.contentResolver.openOutputStream(uri)
                                    ?.use { outputStream ->
                                        inputStream?.copyTo(outputStream)
                                        callback(true)
                                    } ?: callback(false)
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(false)
                    }
                }
            }
        }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = "default_file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun copyUriData(
        contentResolver: ContentResolver,
        sourceUri: Uri,
        destinationUri: Uri
    ) {
        contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                copyStream(inputStream, outputStream)
            }
        } ?: throw RuntimeException("Failed to copy data from $sourceUri to $destinationUri")
    }
}