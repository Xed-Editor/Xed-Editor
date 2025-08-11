package com.rk

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
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.DefaultScope
import com.rk.libcommons.application
import com.rk.libcommons.askInput
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util.copyStream
import java.io.File

object FileManager {
    @SuppressLint("SdCardPath")
    fun requestOpenFromPath(activity: MainActivity) {
        activity.askInput(
            title = strings.path.getString(),
            input = "/sdcard",
            hint = strings.ff_path.getString(),
            onResult = { input ->
                val file = File(input)
                if (input.isEmpty()) {
                    toast(strings.enter_path)
                    return@askInput
                }

                if (!file.exists()) {
                    toast(strings.invalid_path)
                    return@askInput
                }

                if (!file.canRead() || !file.canWrite()) {
                    toast(strings.permission_denied)
                    return@askInput
                }

                if (file.isDirectory) {
                    activity.lifecycleScope.launch {
                        //ProjectManager.addProject(mainActivity, FileWrapper(file))
                        addProject(FileWrapper(file))
                    }
                } else {
                    //mainActivity.adapter!!.addFragment(FileWrapper(file))
                }
            }
        )
    }

    private val gits = mutableSetOf<String>()
    suspend fun findGitRoot(file: File): File? {
        return withContext(Dispatchers.IO) {
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