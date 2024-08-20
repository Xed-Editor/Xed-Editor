package com.rk.xededitor.MainActivity

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.rkUtils

object PermissionManager {
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray,activity: Activity) {

        //check permission for old devices
        if (requestCode == StaticData.REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // permission denied ask again
                verifyStoragePermission(activity)
            }
        }
    }
    fun verifyStoragePermission(activity: Activity) {
        with(activity) {
            var shouldAsk = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    shouldAsk = true
                }
            } else {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    shouldAsk = true
                }
            }

            if (shouldAsk) {
                MaterialAlertDialogBuilder(this).setTitle("Manage Storage")
                    .setMessage("App needs access to edit files in your storage. Please allow the access in the upcoming system setting.")
                    .setNegativeButton("Exit App") { dialog: DialogInterface?, which: Int ->
                        finishAffinity()
                    }.setPositiveButton("OK") { dialog: DialogInterface?, which: Int ->
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.setData(Uri.parse("package:$packageName"))
                            startActivityForResult(intent, StaticData.MANAGE_EXTERNAL_STORAGE)
                        } else {
                            //below 11
                            // Request permissions
                            val perms = arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                            ActivityCompat.requestPermissions(
                                this, perms, StaticData.REQUEST_CODE_STORAGE_PERMISSIONS
                            )
                        }
                    }.setCancelable(false).show()
            }
        }
    }
}