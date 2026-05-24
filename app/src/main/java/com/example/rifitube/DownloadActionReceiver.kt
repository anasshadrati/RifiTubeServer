package com.rifitube.app

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val action = intent.getStringExtra("action") ?: return
        val downloadId = intent.getLongExtra("downloadId", -1L)

        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {

            "cancel" -> {
                if (downloadId != -1L) manager.remove(downloadId)
                notificationManager.cancel(9001)

                Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
            }

            "pause" -> {
                if (downloadId != -1L) manager.remove(downloadId)

                Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
            }

            "resume" -> {
                Toast.makeText(context, "Open app to resume download", Toast.LENGTH_SHORT).show()

                val openIntent = Intent(context, MainActivity::class.java)
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(openIntent)
            }
        }
    }
}