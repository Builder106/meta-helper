package com.metahelper.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log

class WearableService : Service() {
    private val binder = LocalBinder()
    lateinit var glassesManager: GlassesManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): WearableService = this@WearableService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WearableService", "Service created")
        glassesManager = GlassesManager(this)
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MetaHelper Active")
            .setContentText("Connected to your glasses")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Wearable Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        glassesManager.stopAll()
    }

    companion object {
        const val CHANNEL_ID = "WearableServiceChannel"
    }
}

