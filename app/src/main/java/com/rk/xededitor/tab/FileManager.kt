package com.rk.xededitor.tab

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
import com.rk.xededitor.rkUtils
import java.io.File

class FileManager(private val tabActivity: TabActivity) {

    private var requestOpenFile =
        tabActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(convertUriToPath(tabActivity, it.data!!.data))
                tabActivity.addFragment(file)
            }
        }

    private var requestOpenDir =
        tabActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val file = File(convertUriToPath(tabActivity, it.data!!.data))
                ProjectManager.addProject(tabActivity,file)
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
        val popupView = LayoutInflater.from(tabActivity).inflate(R.layout.popup_new, null)
        val editText = popupView.findViewById<View>(R.id.name) as EditText

        editText.setText(Environment.getExternalStorageDirectory().absolutePath)
        editText.hint = "file or folder path"

        MaterialAlertDialogBuilder(tabActivity).setView(popupView).setTitle("Path")
            .setNegativeButton(
                tabActivity.getString(
                    R.string.cancel
                ), null
            ).setPositiveButton("Open") { dialog, which ->
                val path = editText.text.toString()
                val file = File(path)


                if (path.isEmpty()) {
                    rkUtils.toast(tabActivity, "Please enter a path")
                    return@setPositiveButton
                }

                if (!file.exists()) {
                    rkUtils.toast(tabActivity, "Path does not exist")
                    return@setPositiveButton
                }

                if (!file.canRead() && file.canWrite()) {
                    rkUtils.toast(tabActivity, "Permission Denied")
                    return@setPositiveButton
                }


                if (file.isDirectory) {
                    ProjectManager.addProject(tabActivity,file)
                } else {
                    tabActivity.addFragment(file)
                }


            }.show()

    }



}