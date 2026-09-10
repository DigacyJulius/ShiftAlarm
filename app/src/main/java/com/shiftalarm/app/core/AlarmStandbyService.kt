package com.shiftalarm.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shiftalarm.app.MainActivity
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent 24/7 foreground service ("standby" notification).
 *
 * Some OEM battery managers (Xiaomi/Huawei/Oppo/Vivo...) discard a background
 * app's registered alarms even when autostart is enabled. This service keeps
 * the app alive with a silent ongoing notification and runs a watchdog that
 * re-registers every stored alarm every 15 minutes, so even if the system
 * wipes the alarms they come back automatically.
 */
class AlarmStandbyService : Service() {

    companion object {
        const val CHANNEL_ID = "alarm_standby"
        const val NOTIFICATION_ID = 100_000
        private const val WATCHDOG_INTERVAL_MS = 15 * 60_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        scope.launch {
            while (isActive) {
                runCatching { reRegisterAlarms() }
                runCatching { updateNotification() }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    /** Must be called within 5 seconds of start. */
    private fun startInForeground() {
        val notif = buildNotification("保持鬧鐘運作中")
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        }
    }

    /**
     * Watchdog: cancel everything and re-register all stored future alarms.
     * Cheap (pure metadata), and makes the alarm set self-healing.
     */
    private suspend fun reRegisterAlarms() {
        val data = Store(this).data.first()
        val now = System.currentTimeMillis()
        for (old in data.scheduled) AlarmScheduler.cancel(this, old)
        for (e in data.scheduled.filter { it.triggerAt > now }) {
            AlarmScheduler.schedule(this, e)
        }
    }

    private suspend fun updateNotification() {
        val data = Store(this).data.first()
        val now = System.currentTimeMillis()
        val next = data.scheduled.filter { it.triggerAt > now }.minByOrNull { it.triggerAt }
        val text = if (next != null) {
            val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
            "下一個鬧鐘：" + fmt.format(Date(next.triggerAt)) + " " + next.label
        } else {
            "未有排程鬧鐘"
        }
        val notif = buildNotification(text)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, notif) }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("鬧鐘待命中")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "鬧鐘待命", NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "讓鬧鐘在背景保持運作的常駐通知"
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
