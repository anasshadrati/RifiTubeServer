package com.example.rifitube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.DownloadManager
import android.content.Context
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RifiDownloadService : Service() {

    override fun onCreate() {
        super.onCreate()

        val channelId = "rifitube_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RifiTube Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        val cancelIntent =
            Intent(this, RifiDownloadService::class.java).apply {
                action = "CANCEL_DOWNLOAD"
            }

        val cancelPendingIntent =
            PendingIntent.getService(
                this,
                0,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("RifiTube")
                .setContentText("Download service running...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .addAction(
                    android.R.drawable.ic_delete,
                    "Cancel",
                    cancelPendingIntent
                )
                .build()

        startForeground(2222, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == "CANCEL_DOWNLOAD") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}