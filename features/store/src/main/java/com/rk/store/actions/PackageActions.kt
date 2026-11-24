package com.rk.store.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rk.DefaultScope
import com.rk.common.PackageManifest
import com.rk.common.PackageType
import com.rk.common.XedPackage
import com.rk.file.toFileObject
import com.rk.filetree.isXedPackage
import com.rk.filetree.isZip
import com.rk.packages.InstallState
import com.rk.packages.StoreManager
import com.rk.packages.model.Package
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.store.PackageActionContext
import com.rk.store.StoreRegistry
import com.rk.utils.LoadingPopup
import com.rk.utils.application
import com.rk.utils.errorDialog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dispatch of the store's package buttons to the section that owns the package's type.
 *
 * Every function is a no-op when no section is registered for the type, so a package type that is not available in this
 * build simply cannot be operated on instead of taking the store down with it.
 */
fun runPackageInstallAction(pkg: Package, action: PackageActionContext) {
    StoreRegistry.sectionFor(pkg.type)?.install(pkg, action)
}

fun runPackageUpdateAction(pkg: Package, action: PackageActionContext) {
    StoreRegistry.sectionFor(pkg.type)?.update(pkg, action)
}

fun runPackageUninstallAction(pkg: Package, action: PackageActionContext) {
    StoreRegistry.sectionFor(pkg.type)?.uninstall(pkg, action)
}

/** Install state of [pkg], combining in-flight store operations with the state reported by its section. */
@Composable
fun rememberPackageInstallState(pkg: Package): InstallState {
    val activeInstalls by StoreManager.activeInstalls.collectAsStateWithLifecycle()
    return activeInstalls[pkg.id] ?: StoreRegistry.sectionFor(pkg.type)?.installState(pkg) ?: InstallState.Idle
}

/** Progress of the download that is currently running for [id], or 0 when nothing is downloading. */
@Composable
fun rememberDownloadProgress(id: String): Float {
    val downloadProgress by StoreManager.downloadProgress.collectAsStateWithLifecycle()
    return downloadProgress[id] ?: 0f
}

/** Remembers a [PackageActionContext] for the lifetime of the calling screen. */
@Composable
fun rememberPackageActionContext(
    context: Context,
    activity: AppCompatActivity?,
    scope: CoroutineScope,
    updateInstallState: (InstallState) -> Unit = {},
): PackageActionContext {
    val callback by rememberUpdatedState(updateInstallState)
    return remember(context, activity, scope) {
        PackageActionContext(context = context, activity = activity, scope = scope) { state -> callback(state) }
    }
}

/**
 * Downloads the package archive at [url] into the app cache while publishing progress to the store and to a
 * notification. Returns null when the download failed.
 */
suspend fun downloadPackageFile(
    context: Context,
    id: String,
    name: String,
    url: String,
    cacheFileName: String,
    installState: InstallState = InstallState.Installing,
): File? {
    val destFile = File(context.cacheDir, cacheFileName)

    withContext(Dispatchers.Main) {
        StoreManager.setActiveInstall(id, installState)
        StoreManager.setDownloadProgress(id, 0f)
        showDownloadNotification(context, id, name, 0f)
    }

    var lastNotificationTime = 0L
    val downloadSuccess =
        StoreManager.downloadFileWithProgress(
            url = url,
            destFile = destFile,
            onProgress = { progress ->
                DefaultScope.launch(Dispatchers.Main) { StoreManager.setDownloadProgress(id, progress) }
                val now = System.currentTimeMillis()
                if (now - lastNotificationTime > 300) {
                    lastNotificationTime = now
                    showDownloadNotification(context, id, name, progress)
                }
            },
        )

    if (downloadSuccess) {
        showDownloadNotification(context, id, name, 1f)
        return destFile
    }

    destFile.delete()
    return null
}

fun showDownloadNotification(
    context: Context,
    id: String,
    title: String,
    progress: Float,
    isFinished: Boolean = false,
    errorMessage: String? = null,
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "store_downloads"

    val channel =
        NotificationChannel(channelId, "Store Downloads", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notifications for store downloads and installations"
        }
    notificationManager.createNotificationChannel(channel)

    val builder =
        NotificationCompat.Builder(context, channelId).setSmallIcon(drawables.extension).setOnlyAlertOnce(true)

    if (isFinished) {
        if (errorMessage != null) {
            builder
                .setContentTitle(strings.install_failed.getString(context))
                .setContentText(errorMessage)
                .setOngoing(false)
                .setAutoCancel(true)
        } else {
            builder
                .setContentTitle(strings.installed.getString(context))
                .setContentText(title)
                .setOngoing(false)
                .setAutoCancel(true)
        }
    } else {
        builder.setContentTitle(title).setOngoing(true)
        if (progress >= 0f) {
            val percent = (progress * 100).toInt()
            builder.setContentText("$percent%").setProgress(100, percent, false)
        } else {
            builder.setContentText(strings.installing.getString(context)).setProgress(100, 0, true)
        }
    }

    runCatching { notificationManager.notify(id.hashCode(), builder.build()) }
}

/**
 * Installs a package the user opened from storage. The package type is detected from the archive contents and the
 * install is delegated to the section that owns that type.
 */
fun installAutoDetect(scope: CoroutineScope, uri: Uri?, activity: AppCompatActivity?) {
    val loading = InstallLoadingPopup(activity)

    scope.launch(Dispatchers.IO) {
        runCatching {
            if (uri == null) return@runCatching

            val fileObject = uri.toFileObject(expectedIsFile = true)
            if (!fileObject.exists() || !fileObject.canRead()) {
                withContext(Dispatchers.Main) {
                    errorDialog(activity, msg = "Cannot read file: ${fileObject.getAbsolutePath()}")
                }
                return@runCatching
            }

            if (fileObject.getExtension().lowercase() == "json") {
                errorDialog(
                    activity = activity,
                    title = strings.attention.getString(),
                    msg = strings.theme_format_error.getString(),
                )
                return@launch
            }

            if (
                !fileObject.isXedPackage() && !fileObject.isZip() && fileObject.getExtension().lowercase() != "iconpack"
            ) {
                errorDialog(
                    activity = activity,
                    title = strings.attention.getString(),
                    msg = strings.package_format_error.getString(),
                )
                return@launch
            }

            loading.show()

            val tempDir = File(application!!.cacheDir, "install_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                val localFile = File(tempDir, "package.xed")
                fileObject.getInputStream().use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                }

                XedPackage.extract(localFile, tempDir)
                val type = XedPackage.detectPackageType(tempDir)
                val section = type?.let { StoreRegistry.sectionFor(it) }
                val action =
                    PackageActionContext(context = activity ?: application!!, activity = activity, scope = scope)

                if (section == null) {
                    withContext(Dispatchers.Main) {
                        errorDialog(activity, msg = strings.unknown_package_format.getString())
                    }
                    return@runCatching
                }

                // The section owns its own progress and approval UI, so stop our extraction spinner first.
                loading.hide()
                section.installFromFile(localFile, action)
            } finally {
                tempDir.deleteRecursively()
                loading.hide()
            }
        }
            .onFailure { error ->
                loading.hide()
                withContext(Dispatchers.Main) { errorDialog(activity, error) }
            }
    }
}

/** Manifest of a detected package, synthesizing one when the archive predates the manifest format. */
fun detectedManifest(dir: File, type: PackageType?): PackageManifest =
    XedPackage.loadManifest(dir) ?: PackageManifest(type = type)

/**
 * Blocking loading popup shown while a package is installed. Sections that need an approval dialog (e.g. the extension
 * security scan) hide it while the dialog is shown and show it again afterwards.
 */
@Suppress("DEPRECATION")
class InstallLoadingPopup(private val activity: AppCompatActivity?) {
    private var popup: LoadingPopup? = null

    suspend fun show() {
        withContext(Dispatchers.Main) {
            if (popup == null) {
                popup = LoadingPopup(activity).setMessage(strings.installing.getString())
            }
            popup?.show()
        }
    }

    // Not suspending: LoadingPopup marshals to the UI thread itself, so this still runs when the caller's coroutine
    // has already been cancelled (e.g. the dialog was dismissed right after the install finished).
    fun hide() {
        val current = popup
        popup = null
        current?.hide()
    }
}
