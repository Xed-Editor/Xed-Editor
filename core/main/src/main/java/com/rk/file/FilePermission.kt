package com.rk.file

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.application
import com.rk.utils.dialogRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object FilePermission {
    private const val REQUEST_CODE_STORAGE_PERMISSIONS = 1259
    private var isRequesting = false
    private var activeActivity = WeakReference<Activity?>(null)

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        scope: CoroutineScope,
        activity: Activity,
    ) {
        // check permission for old devices
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // permission denied ask again

                scope.launch { verifyStoragePermission(activity) }
            }
        }
    }

    private var dialogRef = WeakReference<AlertDialog?>(null)

    suspend fun verifyStoragePermission(activity: Activity) = withContext(Dispatchers.Main) {
        if (isRequesting && activeActivity.get() == activity) {
            return@withContext
        }
        activeActivity = WeakReference(activity)

        dialogRef.get()?.apply {
            if (isShowing) {
                dismiss()
            }
        }
        dialogRef = WeakReference(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Settings.ignore_storage_permission = false
            }
        }
        if (Settings.ignore_storage_permission) {
            return@withContext
        }
        var shouldAsk = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                shouldAsk = true
            }
        } else {
            if (
                ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
            ) {
                shouldAsk = true
            }
        }

        if (shouldAsk && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q){
            withContext(Dispatchers.Default){
                val app = application ?: return@withContext
                val pm = app.packageManager

                val pkgInfo = pm.getPackageInfo(
                    app.packageName,
                    PackageManager.GET_PERMISSIONS
                )

                shouldAsk =
                    pkgInfo.requestedPermissions?.any {
                        it == android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
                    } ?: false
            }
        }

        if (shouldAsk) {
            isRequesting = true
            dialogRes(
                activity = activity,
                title = strings.manage_storage.getString(),
                msg = strings.manage_storage_reason.getString(),
                cancelRes = strings.ignore,
                okRes = strings.ok,
                onOk = {
                    isRequesting = false
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        val intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:${activity.packageName}".toUri()
                        activity.startActivity(intent)
                    } else {
                        // below 11
                        // Request permissions
                        val perms =
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            )
                        ActivityCompat.requestPermissions(activity, perms, REQUEST_CODE_STORAGE_PERMISSIONS)
                    }
                },
                onCancel = {
                    Settings.ignore_storage_permission = true
                    isRequesting = false
                },
                cancelable = false,
            )
        }
    }
}
