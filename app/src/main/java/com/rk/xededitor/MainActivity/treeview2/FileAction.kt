package com.rk.xededitor.MainActivity.treeview2

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.FileClipboard
import com.rk.xededitor.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData.fragments
import com.rk.xededitor.MainActivity.StaticData.mTabLayout
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter.Companion.stopThread
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

class FileAction(
  private val context: MainActivity,
  private val rootFolder: File,
  private val file: File,
  private val adapter: TreeViewAdapter?
) {

  companion object {
    const val REQUEST_CODE_OPEN_DIRECTORY = 17618
    var to_save_file: File? = null
  }

  private var popupView: View

  init {
    val inflater = context.layoutInflater
    popupView = inflater.inflate(R.layout.file_action, null)
    handleOptionsVisibility()
    val popup = MaterialAlertDialogBuilder(context).setView(popupView).setTitle("Actions").show()


    val root = popupView.findViewById<LinearLayout>(R.id.root)
    for (i in 0 until root.childCount) {
      val child = root.getChildAt(i)
      child.setOnClickListener {
        popup.dismiss()
        handleMenuItemClick(child.id)

      }
    }


  }


  fun handleOptionsVisibility() {
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


    if (file.isDirectory) {
      saveas.visibility = View.GONE
    } else {
      newfile.visibility = View.GONE
      newfolder.visibility = View.GONE
      paste.visibility = View.GONE
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
      R.id.refresh -> {
        // Handle refresh action
        TreeView(context, rootFolder)
        true
      }

      R.id.reselect -> {
        // Handle reselect action
        context.reselctDir(null)
        true
      }

      R.id.openFile -> {
        // Handle openFile action
        context.openFile(null)
        stopThread()
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
        FileAction.to_save_file = file
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(context, intent, FileAction.REQUEST_CODE_OPEN_DIRECTORY, null)
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
        Thread {
          if (!FileClipboard.isEmpty()) {
            val sourceFile = FileClipboard.getFile()
            if (file.isDirectory && sourceFile != null) {
              try {
                val targetPath = file.toPath().resolve(sourceFile.name)

                // Move the source file to the target directory
                Files.copy(
                  sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING
                )

                // Update the TreeView to reflect the changes
                rkUtils.runOnUiThread {
                  adapter?.newFile(file, File(file, sourceFile.name))
                }


                // Optionally, clear the clipboard after pasting
                FileClipboard.clear()

              } catch (e: Exception) {
                e.printStackTrace()
                rkUtils.runOnUiThread {
                  Toast.makeText(context, "Failed to move file: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                }

              }
            }
          }
        }.start()

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
              context.binding.safbuttons.visibility = View.VISIBLE
              context.binding.maindrawer.visibility = View.GONE
              context.binding.drawerToolbar.visibility = View.GONE
              context.adapter?.clear()

            } else {
              if (file.isFile) {

                file.delete()
              } else {

                file.deleteRecursively()
              }

              // TreeView(context, rootFolder)
              adapter?.removeFile(file)

            }


          }.show()







        true
      }

      R.id.close -> {
        // Handle close action
        SettingsData.setSetting(context, "lastOpenedPath", "")
        close()

        true
      }

      else -> false
    }
  }


  fun getMimeType(context: Context, file: File): String? {
    val uri: Uri = Uri.fromFile(file)
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    return if (extension != null) {
      MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
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
        Toast.makeText(context, context.getString(R.string.canthandle), Toast.LENGTH_SHORT).show()
      }
    }catch (e:Exception){
      e.printStackTrace()
      rkUtils.toast(context,"opening this file with other apps is not permitted")
    }
  }

  private fun close() {
    context.adapter?.clear()
    for (i in 0 until mTabLayout.tabCount) {
      val tab = mTabLayout.getTabAt(i)
      if (tab != null) {
        val name = fragments[i].fileName
        tab.setText(name)
      }
    }
    if (mTabLayout.tabCount < 1) {
      context.binding.tabs.visibility = View.GONE
      context.binding.mainView.visibility = View.GONE
      context.binding.openBtn.visibility = View.VISIBLE
    }
    MainActivity.updateMenuItems()
    context.binding.maindrawer.visibility = View.GONE
    context.binding.safbuttons.visibility = View.VISIBLE
    context.binding.drawerToolbar.visibility = View.GONE
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
        adapter?.newFile(file, File(file, fileName))
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
        adapter?.renameFile(file,File(file.parentFile, fileName))
        loading.hide()
      }.show()
  }

}
