package com.rk.xededitor.MainActivity.file

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
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.application
import com.rk.libcommons.askInput
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toFileObject
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.FileAction.Companion.to_save_file
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util.copyStream
import java.io.File

class FileManager(private val mainActivity: MainActivity) {

    private fun getString(@StringRes id:Int):String{
        return id.getString()
    }

    private var requestOpenFile =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                mainActivity.lifecycleScope.launch{
                    delay(100)
                    withContext(Dispatchers.Main){
                        mainActivity.adapter!!.addFragment(it.data!!.data!!.toFileObject())
                    }
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

                mainActivity.lifecycleScope.launch {
                    fileTreeViewModel?.updateCache(parentFile!!)
                }
            }
            parentFile = null
        }

    private var requestOpenDir =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(it.data!!.data!!.toPath())
                if (file.exists() && file.canRead() && file.canWrite()) {
                    mainActivity.lifecycleScope.launch {
                        addProject(FileWrapper(file))
                    }
                } else {
                    runCatching {
                        val takeFlags: Int =
                            (it.data!!.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                        mainActivity.contentResolver.takePersistableUriPermission(
                            it.data!!.data!!, takeFlags
                        )
                    }.onFailure { it.printStackTrace() }

                    val uri = it.data?.data

                    uri?.let {
                        mainActivity.lifecycleScope.launch {
                            addProject(UriWrapper(it))
                        }
                    }


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
                mainActivity.lifecycleScope.launch{
                    val data: Intent? = result.data
                    val wrapper = data?.data?.toFileObject()!!
                    delay(100)
                    withContext(Dispatchers.Main){
                        mainActivity.adapter?.addFragment(wrapper)
                    }

                }

            }
        }


    private var selectDirCallBack:((ActivityResult?)->Unit)? = null
    private var selectDirInternal =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                selectDirCallBack?.invoke(it)
                selectDirCallBack = null
            }
        }


    fun selectDirForNewFileLaunch(fileName:String){
        selectDirCallBack = {
            val file = File(it?.data!!.data!!.toPath())
            val fileObject = if (file.exists().not()) {
                runCatching {
                    val takeFlags: Int =
                        (it.data!!.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                    mainActivity.contentResolver.takePersistableUriPermission(
                        it.data!!.data!!, takeFlags
                    )
                }
                UriWrapper(it.data!!.data!!)
            } else {
                FileWrapper(file)
            }

            if (fileObject.hasChild(fileName).not()){
                toast("File with name $fileName already exists")
            }else{
                val newFile = fileObject.createChild(true,fileName)
                DefaultScope.launch(Dispatchers.Main) {
                    if (newFile != null) {
                        mainActivity.adapter?.addFragment(newFile)
                    }else{
                        toast("Unable to create file")
                    }
                }
            }

        }
        selectDirInternal.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
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
                errorDialog(it)
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
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        val activities = application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        if (activities.isNotEmpty()){
            requestToSaveFile.launch(intent)
        }else{
            selectDirCallBack = {
                val sourceUri = to_save_file!!.toUri()

                mainActivity.contentResolver.openInputStream(sourceUri)
                    .use { inputStream ->
                        mainActivity.contentResolver.openOutputStream(
                            it?.data!!.data!!
                        )?.use { outputStream ->
                            copyStream(inputStream, outputStream)
                        }
                    }
            }
            selectDirInternal.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }

    }

    @SuppressLint("SdCardPath")
    fun requestOpenFromPath() {
        mainActivity.askInput(
            title = strings.path.getString(),
            input = "/sdcard",
            hint = strings.ff_path.getString(),
            onResult = { input ->
                val file = File(input)
                if (input.isEmpty()) {
                    toast(getString(strings.enter_path))
                    return@askInput
                }

                if (!file.exists()) {
                    toast(getString(strings.invalid_path))
                    return@askInput
                }

                if (!file.canRead() || !file.canWrite()) {
                    toast(getString(strings.permission_denied))
                    return@askInput
                }

                if (file.isDirectory) {
                    mainActivity.lifecycleScope.launch {
                        //ProjectManager.addProject(mainActivity, FileWrapper(file))
                        addProject(FileWrapper(file))
                    }
                } else {
                    mainActivity.adapter!!.addFragment(FileWrapper(file))
                }
            }
        )
    }

    companion object {
        private val gits = mutableSetOf<String>()
        suspend fun findGitRoot(file: File): File? {
            return withContext(Dispatchers.IO){
                gits.forEach { root ->
                    if (file.absolutePath.contains(root)) {
                        return@withContext File(root)
                    }
                }
                var currentFile = file
                while (currentFile.parentFile != null) {
                    if (File(currentFile.parentFile, ".git").exists()) {
                        currentFile.parentFile?.let { gits.add(it.absolutePath) }
                        return@withContext currentFile.parentFile
                    }
                    currentFile = currentFile.parentFile!!
                }
                return@withContext null
            }

        }
    }
}
