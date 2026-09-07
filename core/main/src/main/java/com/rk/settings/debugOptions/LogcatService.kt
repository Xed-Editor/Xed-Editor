package com.rk.settings.debugOptions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import com.rk.activities.main.MainActivity
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LogcatService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var logcatProcess: Process? = null

    companion object {
        private const val CHANNEL_ID = "logcat_service_channel"

        val logcatLogs = mutableStateListOf<String>()

        private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 512)
        val logFlow = _logFlow.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, LogcatService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LogcatService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun shouldCaptureLog(line: String): Boolean {
        if (line.isEmpty()) {
            return false
        }

        // Filter out known noisy system verbose logs
        val noisyKeywords =
            listOf(
                "ViewRootImpl",
                "Choreographer",
                "OpenGLRenderer",
                "InputMethodManager",
                "RenderThread",
                "libEGL",
                "EGL_emulation",
                "CompatChangeReporter",
                "vndksupport",
                "ConfigStore",
                "Mono",
                "me.weishu.reflection",
                "chatty",
                "HostConnection",
                "gralloc",
                "GestureDetector",
                "WindowManager",
                "ImeFocusController",
                "Insets",
            )
        if (noisyKeywords.any { line.contains(it, ignoreCase = true) }) {
            return false
        }

        if (!line.contains('/') || !line.contains('(')) {
            return true
        }

        val slashIdx = line.indexOf('/')
        val parenIdx = line.indexOf('(')
        if (slashIdx != 1 || parenIdx <= slashIdx) {
            return true
        }

        val priority = line[0]
        val tag = line.substring(slashIdx + 1, parenIdx).trim()

        // 1. Capture warning + error + fatal + assert logs
        if (priority == 'W' || priority == 'E' || priority == 'F' || priority == 'A') {
            return true
        }

        // 2. Capture System.* calls
        if (tag.startsWith("System.out") || tag.startsWith("System.err")) {
            return true
        }

        // 3. Default to capturing
        return true
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(99, notification)

        // Start collecting logcat
        serviceScope.launch {
            try {
                // Clear any existing logs
                launch(Dispatchers.Main) {
                    logcatLogs.clear()
                }

                // Run logcat continuously in brief format
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "brief"))
                logcatProcess = process

                val batch = mutableListOf<String>()
                var lastUpdateTime = System.currentTimeMillis()

                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (shouldCaptureLog(line)) {
                            _logFlow.tryEmit(line)
                            synchronized(batch) {
                                batch.add(line)
                            }

                            val now = System.currentTimeMillis()
                            if (batch.size >= 50 || now - lastUpdateTime > 100) {
                                val itemsToAdd =
                                    synchronized(batch) {
                                        val copy = batch.toList()
                                        batch.clear()
                                        copy
                                    }
                                lastUpdateTime = now
                                launch(Dispatchers.Main) {
                                    synchronized(logcatLogs) {
                                        logcatLogs.addAll(itemsToAdd)
                                        if (logcatLogs.size > 1000) {
                                            val toRemove = logcatLogs.size - 1000
                                            logcatLogs.removeRange(0, toRemove)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add any remaining items
                if (batch.isNotEmpty()) {
                    launch(Dispatchers.Main) {
                        synchronized(logcatLogs) {
                            logcatLogs.addAll(batch)
                            if (logcatLogs.size > 1000) {
                                val toRemove = logcatLogs.size - 1000
                                logcatLogs.removeRange(0, toRemove)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            Settings.enable_logcat = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        logcatProcess?.destroy()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(99)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(CHANNEL_ID, "Logcat Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notification for Logcat Background Service"
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val stopIntent = Intent(this, LogcatService::class.java).apply { action = "ACTION_STOP" }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(strings.logcat_running.getString())
            .setContentText(strings.logcat_running_desc.getString())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(NotificationCompat.Action.Builder(null, strings.stop.getString(), stopPendingIntent).build())
            .setOngoing(true)
            .build()
    }
}
