package com.rk.xededitor.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.rk.xededitor.ui.activities.settings.Terminal
import com.rk.xededitor.ui.screens.settings.terminal.MkSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import androidx.core.app.NotificationCompat
import com.rk.resources.drawables

class SessionService : Service() {

    private val sessions = hashMapOf<String,TerminalSession>()

    inner class SessionBinder : Binder() {
        fun createSession(id: String,client: TerminalSessionClient,activity: Activity):TerminalSession{
            return MkSession.createSession(activity,client).also { sessions[id] = it }
        }
        fun getSession(id: String):TerminalSession?{
            return sessions[id]
        }
        fun terminateSession(id:String){
            sessions[id]?.finishIfRunning()
            sessions.remove(id)
        }
    }

    private val binder = SessionBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        sessions.forEach{s -> s.value.finishIfRunning()}
        super.onDestroy()
    }


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }


    private fun createNotification(): Notification {
        val intent = Intent(this, Terminal::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Session Service")
            .setContentText("The service is running in the background")
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
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
            description = "Notification for Session Service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

}
