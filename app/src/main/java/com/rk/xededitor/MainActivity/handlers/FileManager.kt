package com.rk.xededitor.MainActivity.handlers

import android.content.DialogInterface
import android.content.Intent
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.After
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.FileClipboard
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.PathUtils.convertUriToPath
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.fragment.AutoSaver
import com.rk.xededitor.MainActivity.treeview2.FileAction
import com.rk.xededitor.MainActivity.treeview2.FileAction.Companion.Staticfile
import com.rk.xededitor.MainActivity.treeview2.TreeView
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter.Companion.stopThread
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.text.ContentIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val delimiter = "|$|"
object FileManager {

//    @OptIn(DelicateCoroutinesApi::class)
//    fun addFileToPreviouslyOpenedFiles(file: File){
//        GlobalScope.launch(Dispatchers.Default){
//            val files = SettingsData.getString(Keys.LAST_OPENED_FILES,"")
//            if (files.contains(file.absolutePath)){
//                return@launch
//            }
//            val sb = StringBuilder(files)
//            sb.append(file.absolutePath).append(delimiter)
//            SettingsData.setString(Keys.LAST_OPENED_FILES,sb.toString())
//        }
//    }

//    @OptIn(DelicateCoroutinesApi::class)
//    fun removeFileFromPreviouslyOpenedFiles(file: File){
//        GlobalScope.launch(Dispatchers.Default){
//            val files = SettingsData.getString(Keys.LAST_OPENED_FILES,"")
//            val filesSplit = files.split(delimiter)
//            val sb = StringBuilder()
//            filesSplit.forEach { fileStr ->
//                if (fileStr != file.absolutePath){
//                    sb.append(fileStr).append(delimiter)
//                }
//            }
//        }
//    }

//    fun loadPreviouslyOpenedFiles(activity: MainActivity){
//        activity.lifecycleScope.launch(Dispatchers.Default){
//            val lastOpenedFiles = SettingsData.getString(Keys.LAST_OPENED_FILES,"")
//            if (lastOpenedFiles.isEmpty()){
//                return@launch
//            }
//            val loadingPopup = LoadingPopup(activity,null).show()
//
//            val filesStr = lastOpenedFiles.split(delimiter)
//            filesStr.forEach { fileStr ->
//                launch(Dispatchers.Default){
//                    val file = File(fileStr)
//                    if (file.exists().and(file.isFile)){
//                        withContext(Dispatchers.Main){
//                            activity.newEditor(file)
//                            activity.adapter?.onNewEditor(file)
//                        }
//                    }else{
//                        Log.e("loadPreviouslyOpenedFiles","file $fileStr does not exist or its a directory \n\n\n  $fileStr")
//                    }
//                }
//
//            }
//            delay(50)
//            loadingPopup.hide()
//        }
//    }

    fun saveFile(activity: MainActivity, index: Int, isAutoSaver: Boolean = false) {
        if (index > fragments.size){
            return
        }
        val fragment = fragments[index]
        val file = fragment.file
        val content = fragment.content

        if (file == null || file.exists().not()){
            if (isAutoSaver.not()){
                rkUtils.runOnUiThread{
                    Toast.makeText(activity, "File no longer exists", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        if (isAutoSaver && content.isNullOrEmpty()){
            return
        }

        rkUtils.runOnUiThread {
            val tab = mTabLayout.getTabAt(index) ?: throw RuntimeException("Tab not found")
            if (tab.text?.endsWith("*") == true) {
                fragment.isModified = false
                tab.text = tab.text?.dropLast(1)
            }
        }

        activity.lifecycleScope.launch(Dispatchers.IO){
            try {
                val outputStream = FileOutputStream(file, false)
                if (content != null) {
                    ContentIO.writeTo(content, outputStream, true)
                }
            }catch (e:Exception){
                e.printStackTrace()
                withContext(Dispatchers.Main){
                    rkUtils.toast(activity,e.message)
                }
            }

        }

        if (isAutoSaver.not()) {
            rkUtils.toast(activity, activity.getString(R.string.save))
        }


    }

    fun handleSaveAllFiles(activity: MainActivity, isAutoSaver: Boolean = false) {
        //loop over all tabs and save the files
        for (i in 0 until mTabLayout.tabCount) {
            saveFile(activity, i, isAutoSaver)
        }

        if (!isAutoSaver) {
            After(100) {
                rkUtils.runOnUiThread {
                    rkUtils.toast(activity, activity.getString(R.string.saveAll))
                }
            }
        }


    }

    fun handleAddFile(data: Intent?, mainActivity: MainActivity) {
        val selectedFile = File(convertUriToPath(mainActivity, data!!.data))
        val targetFile = Staticfile

        if (targetFile != null && targetFile.isDirectory && selectedFile.exists() && selectedFile.isFile) {
            try {
                val destinationPath = File(targetFile, selectedFile.name).toPath()
                Files.move(
                    selectedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING
                )
                if (targetFile.absolutePath == StaticData.rootFolder.absolutePath) {
                    TreeView(mainActivity, StaticData.rootFolder)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("FileAction", "Failed to move file: " + e.message)
            }
        }
    }


    fun handleOpenDirectory(data: Intent?, mainActivity: MainActivity) {
        with(mainActivity) {
            val directoryUri = data!!.data
            if (directoryUri != null) {
                val directory = File(convertUriToPath(this, directoryUri))
                if (directory.isDirectory) {
                    if (!directory.exists()) {
                        directory.mkdirs()
                    }
                    val newFile = File(directory, FileAction.to_save_file!!.name)

                    try {
                        Files.copy(
                            FileAction.to_save_file!!.toPath(),
                            newFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )

                        //clear file clipboard
                        FileClipboard.clear()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        throw RuntimeException("Failed to save file: " + e.message)
                    }
                } else {
                    throw RuntimeException("Selected path is not a directory")
                }
            }
        }

    }

    fun handleDirectorySelection(data: Intent, mainActivity: MainActivity) {
        with(mainActivity) {
            binding.mainView.visibility = View.VISIBLE
            binding.safbuttons.visibility = View.GONE
            binding.maindrawer.visibility = View.VISIBLE
            binding.drawerToolbar.visibility = View.VISIBLE

            val file = File(convertUriToPath(this, data.data))
            StaticData.rootFolder = file
            var name = StaticData.rootFolder.name
            if (name.length > 18) {
                name = StaticData.rootFolder.name.substring(0, 15) + "..."
            }
            binding.rootDirLabel.text = name
            TreeView(this, file)
        }

    }

    fun handleFileSelection(data: Intent, mainActivity: MainActivity) {
        with(mainActivity) {
            binding.tabs.visibility = View.VISIBLE
            binding.mainView.visibility = View.VISIBLE
            binding.openBtn.visibility = View.GONE
            newEditor(File(convertUriToPath(this, data.data)))

        }
    }

    fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        BaseActivity.getActivity(MainActivity::class.java)?.startActivityForResult(intent,
            StaticData.REQUEST_FILE_SELECTION
        )
    }
    fun openDir() {
        stopThread()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        BaseActivity.getActivity(MainActivity::class.java)?.startActivityForResult(intent,
            StaticData.REQUEST_DIRECTORY_SELECTION
        )
    }
    fun openFromPath() {
        BaseActivity.getActivity(MainActivity::class.java)?.let {
            with(it){
                val popupView = LayoutInflater.from(this).inflate(R.layout.popup_new, null)
                val editText = popupView.findViewById<View>(R.id.name) as EditText

                editText.setText(Environment.getExternalStorageDirectory().absolutePath)
                editText.hint = "file or folder path"

                MaterialAlertDialogBuilder(this).setView(popupView).setTitle("Path").setNegativeButton(
                    getString(
                        R.string.cancel
                    ), null
                ).setPositiveButton("Open", DialogInterface.OnClickListener { dialog, which ->
                    val path = editText.text.toString()
                    if (path.isEmpty()) {
                        rkUtils.toast(this, "Please enter a path")
                        return@OnClickListener
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        rkUtils.toast(this, "Path does not exist")
                        return@OnClickListener
                    }

                    if (!file.canRead() && file.canWrite()) {
                        rkUtils.toast(this, "Permission Denied")
                    }
                    if (file.isDirectory) {
                        binding.mainView.visibility = View.VISIBLE
                        binding.safbuttons.visibility = View.GONE
                        binding.maindrawer.visibility = View.VISIBLE
                        binding.drawerToolbar.visibility = View.VISIBLE

                        StaticData.rootFolder = file

                        TreeView(this, file)

                        //use new file browser
                        var name = StaticData.rootFolder.name
                        if (name.length > 18) {
                            name = StaticData.rootFolder.name.substring(0, 15) + "..."
                        }

                        binding.rootDirLabel.text = name
                    } else {
                        newEditor(file)
                    }
                }).show()
            }
        }
    }
    fun privateDir() {
        BaseActivity.getActivity(MainActivity::class.java)?.let {
            with(it) {
                binding.mainView.visibility = View.VISIBLE
                binding.safbuttons.visibility = View.GONE
                binding.maindrawer.visibility = View.VISIBLE
                binding.drawerToolbar.visibility = View.VISIBLE

                val file = filesDir.parentFile

                StaticData.rootFolder = file

                if (file != null) {
                    TreeView(this, file)
                }

                var name = StaticData.rootFolder.name
                if (name.length > 18) {
                    name = StaticData.rootFolder.name.substring(0, 15) + "..."
                }

                binding.rootDirLabel.text = name
            }
        }
    }
}