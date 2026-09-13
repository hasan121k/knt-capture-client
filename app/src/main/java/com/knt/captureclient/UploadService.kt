package com.knt.captureclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class UploadService : Service() {

    private var exec: ScheduledExecutorService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotif()

        if (exec == null || exec!!.isShutdown) {
            exec = Executors.newSingleThreadScheduledExecutor()
            exec!!.scheduleWithFixedDelay({
                try { CaptureUploader.flushNow() } catch (_: Exception) {}
            }, 0, 2, TimeUnit.SECONDS)
        }
        return START_STICKY
    }

    private fun startForegroundNotif() {
        val chId = "knt_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(chId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(chId, "KNT Capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, chId)
        else @Suppress("DEPRECATION") Notification.Builder(this)

        val n = builder
            .setContentTitle("KNT Capture")
            .setContentText("running")
            .setSmallIcon(android.R.drawable.presence_online)
            .build()

        startForeground(1, n)
    }

    override fun onDestroy() {
        try { exec?.shutdownNow() } catch (_: Exception) {}
        super.onDestroy()
    }
}
