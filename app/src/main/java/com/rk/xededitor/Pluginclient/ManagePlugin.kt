package com.rk.xededitor.Pluginclient

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libPlugin.server.PluginInstaller
import com.rk.libPlugin.server.PluginUtils
import com.rk.libPlugin.server.PluginUtils.indexPlugins
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.ActionPopup
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.databinding.ActivityPluginsBinding
import com.rk.xededitor.rkUtils


const val PICK_FILE_REQUEST_CODE = 37579

class ManagePlugin : BaseActivity() {
    lateinit var binding: ActivityPluginsBinding
    lateinit var madapter: InstalledPluginListAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginsBinding.inflate(layoutInflater)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = "Manage Plugins"

        setContentView(binding.root)


        if (SettingsData.isDarkMode(this) && SettingsData.isOled()) {
            binding.root.setBackgroundColor(Color.BLACK)
            binding.toolbar.setBackgroundColor(Color.BLACK)
            binding.appbar.setBackgroundColor(Color.BLACK)
            window.navigationBarColor = Color.BLACK
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        } else if (SettingsData.isDarkMode(this)) {
            val window = window
            window.navigationBarColor = Color.parseColor("#141118")
        }

        application.indexPlugins()
        madapter = InstalledPluginListAdapter(
            this,
            PluginUtils.getInstalledPlugins()
        )
        binding.listView.adapter = madapter

        binding.fab.setOnClickListener {



            ActionPopup(this).apply {
                setTitle("Install Plugin")

                addItem("Zip Install","Install Plugin from local storage",ContextCompat.getDrawable(this@ManagePlugin,com.rk.libcommons.R.drawable.archive),
                    {
                        hide()
                        MaterialAlertDialogBuilder(this@ManagePlugin).setTitle("Add Plugin")
                            .setMessage("Choose the plugin zip file from storage to install it.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Choose") { dialog, which ->
                                val intent = Intent(Intent.ACTION_GET_CONTENT)
                                intent.type = "*/*"
                                startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
                            }.show()


                    }, View.generateViewId())


                addItem("Download","Download Plugins from repository",ContextCompat.getDrawable(this@ManagePlugin,R.drawable.download),
                    {
                        hide()
                        startActivity(Intent(this@ManagePlugin,ActivityPluginRepo::class.java))
                    }, View.generateViewId())
                getDialogBuilder().setNegativeButton("Cancel",null)
                show()
            }



        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == PICK_FILE_REQUEST_CODE) {
            data?.data?.let { uri ->
                val fileName = getFileName(uri).toString()
                if (fileName.endsWith(".zip").not()) {
                    rkUtils.toast(this, "Invalid file type, zip file expected")
                    return
                }


                val isInstalled = contentResolver.openInputStream(uri)?.let { PluginInstaller.installFromZip(this, it) } ?: false

                if (isInstalled){
                    rkUtils.toast(this, "Installed Successfully")
                    recreate()
                }else{
                    rkUtils.toast(this, "Failed to install")
                }




            }
        }


    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                result = if (nameIndex != -1) {
                    it.getString(nameIndex)
                } else {
                    null
                }
            }
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here
        val id = item.itemId
        if (id == android.R.id.home) {
            // Handle the back arrow click here
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }



}