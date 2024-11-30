package com.rk.xededitor.MainActivity.handlers

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PermissionHandler {
    private const val REQUEST_CODE_STORAGE_PERMISSIONS = 1259
    private const val MANAGE_EXTERNAL_STORAGE = 98421
    
    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        activity: MainActivity,
    ) {
        // check permission for old devices
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // permission denied ask again
                activity.lifecycleScope.launch { verifyStoragePermission(activity) }
                
            }
        }
    }
    
    suspend fun verifyStoragePermission(activity: MainActivity) {
        withContext(Dispatchers.Default) {
            var shouldAsk = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                withContext(Dispatchers.Main) {
                    if (!Environment.isExternalStorageManager()) {
                        shouldAsk = true
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (ContextCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        shouldAsk = true
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                if (shouldAsk) {
                    MaterialAlertDialogBuilder(activity).setTitle(getString(strings.manage_storage))
                        .setMessage(getString(strings.manage_storage_reason))
                        .setPositiveButton(getString(strings.ok)) { dialog: DialogInterface?, which: Int ->
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.setData(Uri.parse("package:${activity.packageName}"))
                                activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE)
                            } else {
                                // below 11
                                // Request permissions
                                val perms = arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                )
                                ActivityCompat.requestPermissions(
                                    activity,
                                    perms,
                                    REQUEST_CODE_STORAGE_PERMISSIONS,
                                )
                            }
                        }.setCancelable(false).show()
                }
            }
        }
    }
}
