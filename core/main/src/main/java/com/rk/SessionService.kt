package com.rk

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.terminal.Terminal
import com.rk.xededitor.ui.screens.terminal.MkSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class SessionService : Service() {
    private val sessions = hashMapOf<String, TerminalSession>()
    val sessionList = mutableStateListOf<String>()
    var currentSession = mutableStateOf<String>("main")

    inner class SessionBinder : Binder() {
        fun getService(): SessionService {
            return this@SessionService
        }

        fun createSession(
            id: String,
            client: TerminalSessionClient,
            activity: Terminal
        ): TerminalSession {
            return MkSession.createSession(activity, client, id).also {
                sessions[id] = it
                sessionList.add(id)
                updateNotification()
            }
        }

        fun getSession(id: String): TerminalSession? {
            return sessions[id]
        }

        fun terminateSession(id: String) {
            sessions[id]?.apply {
                if (emulator != null) {
                    sessions[id]?.finishIfRunning()
                }
            }
            sessions.remove(id)
            sessionList.remove(id)
            if (sessions.isEmpty()) {
                stopSelf()
            } else {
                updateNotification()
            }
        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        sessions.forEach { s -> s.value.finishIfRunning() }
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                sessions.forEach { s -> s.value.finishIfRunning() }
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, Terminal::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = "ACTION_EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${strings.app_name.getString()} Terminal")
            .setContentText(getNotificationContentText())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "EXIT",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "session_service_channel"

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Terminal Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(1, notification)
    }

    private fun getNotificationContentText(): String {
        val count = sessions.size
        return "$count sessions running"
    }
}
