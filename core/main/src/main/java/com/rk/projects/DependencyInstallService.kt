package com.rk.projects

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rk.exec.ShellUtils
import com.rk.resources.drawables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class DepInstallStatus {
    PENDING,
    INSTALLING,
    DONE,
    FAILED,
}

/**
 * Observable, process-wide progress of the background dependency install. The dialog observes this;
 * the [DependencyInstallService] writes to it. Survives the dialog/app being backgrounded.
 */
object DependencyInstaller {
    val status = mutableStateMapOf<String, DepInstallStatus>()

    var running by mutableStateOf(false)
        internal set

    var currentName by mutableStateOf("")
        internal set

    fun prime(names: List<String>) {
        status.clear()
        names.forEach { status[it] = DepInstallStatus.PENDING }
        currentName = ""
    }
}

/**
 * Foreground service that installs apt packages in the sandbox sequentially, posting a progress
 * notification. Because it's a started foreground service, installs keep running even if the user
 * leaves the app.
 */
class DependencyInstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification("Preparing…", 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val names = intent?.getStringArrayListExtra(EXTRA_NAMES) ?: arrayListOf()
        val commands = intent?.getStringArrayListExtra(EXTRA_COMMANDS) ?: arrayListOf()
        if (names.isEmpty()) {
            stopSelfCompat()
            return START_NOT_STICKY
        }

        DependencyInstaller.prime(names)
        DependencyInstaller.running = true

        scope.launch {
            val total = names.size
            for (i in names.indices) {
                val name = names[i]
                val command = commands.getOrElse(i) { "" }
                DependencyInstaller.currentName = name
                DependencyInstaller.status[name] = DepInstallStatus.INSTALLING
                updateNotification("Installing $name", i, total)

                val result =
                    runCatching {
                            ShellUtils.runUbuntu(command = arrayOf("bash", "-lc", command), timeoutSeconds = 3600L)
                        }
                        .getOrNull()

                DependencyInstaller.status[name] =
                    if (result != null && result.exitCode == 0) DepInstallStatus.DONE else DepInstallStatus.FAILED
            }
            DependencyInstaller.currentName = ""
            DependencyInstaller.running = false
            updateNotification("Finished", total, total)
            stopSelfCompat()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DependencyInstaller.running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfCompat() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun updateNotification(text: String, current: Int, total: Int) {
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(text, current, total)) }
    }

    private fun buildNotification(text: String, current: Int, total: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Installing dependencies")
            .setContentText(text)
            .setSmallIcon(drawables.download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .apply { if (total > 0) setProgress(total, current, false) }
            .build()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Dependency installer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of background dependency installs"
            }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 7421
        private const val CHANNEL_ID = "dependency_install_channel"
        private const val EXTRA_NAMES = "dep_names"
        private const val EXTRA_COMMANDS = "dep_commands"

        /** Starts the background install for the given tool [names] and their full shell [commands]. */
        fun start(context: Context, names: ArrayList<String>, commands: ArrayList<String>) {
            val intent =
                Intent(context, DependencyInstallService::class.java).apply {
                    putStringArrayListExtra(EXTRA_NAMES, names)
                    putStringArrayListExtra(EXTRA_COMMANDS, commands)
                }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
