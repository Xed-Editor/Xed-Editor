package com.rk.xededitor.MainActivity.handlers

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object PermissionHandler {
    private const val REQUEST_CODE_STORAGE_PERMISSIONS = 1259

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

    private var dialogRef = WeakReference<AlertDialog?>(null)
    fun verifyStoragePermission(activity: MainActivity) {
        dialogRef.get()?.apply {
            if (isShowing){
                dismiss()
            }
        }
        dialogRef = WeakReference(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()){
                Settings.ignore_storage_permission = false
            }
        }
        if (Settings.ignore_storage_permission){
            return
        }
        var shouldAsk = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                shouldAsk = true
            }
        }else{
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
        if (shouldAsk) {
            MaterialAlertDialogBuilder(activity).apply {
                setCancelable(false)
                setTitle(strings.manage_storage)
                setMessage(strings.manage_storage_reason)

                setNegativeButton(strings.ignore.getString() + " (Experimental)"){ _,_ ->
                    Settings.ignore_storage_permission = true
                }
                setPositiveButton(strings.ok) { _, _ ->
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        val intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.setData(Uri.parse("package:${activity.packageName}"))
                        activity.startActivity(intent)
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

                    
                }
                dialogRef = WeakReference(show())
            }
        }
    }
}