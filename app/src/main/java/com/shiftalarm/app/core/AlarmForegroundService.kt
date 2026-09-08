package com.shiftalarm.app.core

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
        if (id == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        val launch = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullPi = PendingIntent.getActivity(
            this, id.toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlarmRingingActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("\u9b27\u9418\u97ff\u8d77")
            .setContentText("\u9ede\u6309\u67e5\u770b")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, id.toInt(), notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(id.toInt(), notif)
            }
        }

        runCatching { startActivity(launch) }

        releaseWakeLock()
        stopSelf()
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShiftAlarm:AlarmWakeLock"
            ).apply { acquire(15_000L) }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}
