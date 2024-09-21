package com.rk.xededitor.MainActivity.file

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.MainActivity.file.PathUtils.convertUriToPath
import com.rk.xededitor.R
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.rkUtils
import java.io.File

class FileManager(private val mainActivity: MainActivity) {

    private var requestOpenFile =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(convertUriToPath(mainActivity, it.data!!.data))
                mainActivity.adapter.addFragment(file)
            }
        }

    private var requestOpenDir =
        mainActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(convertUriToPath(mainActivity, it.data!!.data))
                ProjectManager.addProject(mainActivity,file)
            }
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

        editText.setText(Environment.getExternalStorageDirectory().absolutePath)
        editText.hint = "file or folder path"

        MaterialAlertDialogBuilder(mainActivity).setView(popupView).setTitle("Path")
            .setNegativeButton(
                mainActivity.getString(
                    R.string.cancel
                ), null
            ).setPositiveButton("Open") { dialog, which ->
                val path = editText.text.toString()
                val file = File(path)


                if (path.isEmpty()) {
                    rkUtils.toast( "Please enter a path")
                    return@setPositiveButton
                }

                if (!file.exists()) {
                    rkUtils.toast( "Path does not exist")
                    return@setPositiveButton
                }

                if (!file.canRead() && file.canWrite()) {
                    rkUtils.toast("Permission Denied")
                    return@setPositiveButton
                }


                if (file.isDirectory) {
                    ProjectManager.addProject(mainActivity,file)
                } else {
                    mainActivity.adapter.addFragment(file)
                }


            }.show()

    }
    
    companion object{
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