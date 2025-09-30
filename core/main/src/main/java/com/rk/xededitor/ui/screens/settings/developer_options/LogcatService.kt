package com.rk

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rk.file.child
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LogcatService : Service(),
    CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    private var process: Process? = null

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        runCatching {
            if (process?.isAlive == true) {
                process?.destroy()
            }
            cancel()
        }.onFailure {
            it.printStackTrace()
            errorDialog(it)
        }

        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(2, notification)
        launch(Dispatchers.IO) {
            runCatching {
                process = ProcessBuilder("logcat")
                    .redirectOutput(sandboxHomeDir().child("logcat.txt"))
                    .redirectErrorStream(true)
                    .start()
            }.onFailure {
                it.printStackTrace()
                errorDialog(it)
                stopSelf()
            }

        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_DONE" -> {
                toast(strings.logcat_saved)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, LogcatService::class.java).apply {
            action = "ACTION_DONE"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(strings.logcat_service.getString())
            .setContentText(strings.logcat_service_desc.getString())
            .setSmallIcon(drawables.android)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    strings.stop_logcat.getString(),
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "logcat_service_channel"

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.logcat_service.getString(),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Logcat Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

}
