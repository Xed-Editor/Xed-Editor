package com.rk.libcommons

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VirtualService : Service() {
    
    private val handlerThread = HandlerThread("BaseLspServiceThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
    
    override fun onBind(intent: Intent?): IBinder? {
        // Return null since this is a background service, not a bound service
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runnableId = intent?.getStringExtra(EXTRA_RUNNABLE_ID)
        runnableId?.let {
            val runnable = runnableMap[it]
            if (runnable != null) {
                Log.d(TAG, "Executing runnable with ID: $it")
                handler.post(runnable)
                runnableMap.remove(it) // Remove the task once it's executed
            } else {
                Log.d(TAG, "Runnable with ID: $it not found")
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service is being destroyed")
        handlerThread.quitSafely()
    }
    
    companion object {
        private const val TAG = "BaseService"
        private const val EXTRA_RUNNABLE_ID = "extra_runnable_id"
        private val runnableMap = ConcurrentHashMap<String, Runnable>()
        
        /**
         * Launches the service with a new runnable task.
         *
         * @param context The context to use for starting the service.
         * @param runnable The task to execute in the service.
         */
        @Keep
        fun launchService(context: Context, runnable: Runnable) {
            val runnableId = UUID.randomUUID().toString() // Generate a unique ID for each task
            runnableMap[runnableId] = runnable
            
            val intent = Intent(context, VirtualService::class.java).apply {
                putExtra(EXTRA_RUNNABLE_ID, runnableId)
            }
            context.startService(intent)
            Log.d(TAG, "Service started with runnable ID: $runnableId")
        }
    }
}
