package com.shiftalarm.app.core

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alarmId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        val launch = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullPi = PendingIntent.getActivity(
            this, alarmId.toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlarmRingingActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("\u9b27\u9418\u97ff\u8d77")
            .setContentText("\u9ede\u6309\u67e5\u770b")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullPi, true)
            .setAutoCancel(false) // Keep notification until explicitly dismissed
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, alarmId.toInt(), notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(alarmId.toInt(), notif)
            }
        }

        // Schedule activity launch with retry mechanism for doze mode
        handler.postDelayed({
            runCatching { 
                startActivity(launch) 
            }
            // Keep service alive for a bit longer to ensure alarm handling
            handler.postDelayed({
                cleanupAndStop()
            }, 30_000) // Keep service alive for 30 seconds
        }, 1000) // Small delay to ensure foreground service is properly started

        // Don't stop immediately - let the delayed cleanup handle it
        return START_STICKY // Restart if killed unexpectedly
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "ShiftAlarm:AlarmWakeLock"
            ).apply { 
                // Acquire without timeout for critical alarm handling
                acquire() 
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }
    
    private fun cleanupAndStop() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        super.onDestroy()
    }
}
