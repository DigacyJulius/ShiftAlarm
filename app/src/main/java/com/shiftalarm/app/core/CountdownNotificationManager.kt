package com.shiftalarm.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shiftalarm.app.MainActivity
import com.shiftalarm.app.R
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Singleton: shows a silent countdown notification for the next alarm within
 * 10 minutes. The update loop re-reads the stored schedule every tick, so a
 * deleted alarm's notification disappears immediately instead of counting
 * down to a fire time that no longer exists.
 */
object CountdownNotificationManager {

    const val CHANNEL_ID = "upcoming_alarm"
    const val NOTIFICATION_ID = 99999
    private const val COUNTDOWN_THRESHOLD_MINUTES = 10L

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null
    private var appContext: Context? = null

    private fun contextOf(context: Context): Context {
        if (appContext == null) appContext = context.applicationContext
        return appContext!!
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "即將鬧鐘",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "提示即將響起的鬧鐘"
            nm.createNotificationChannel(channel)
        }
    }

    suspend fun checkAndShowCountdown(context: Context) {
        val c = contextOf(context)
        ensureChannel(c)

        // Cancel any existing update job
        updateJob?.cancel()

        val data = Store(c).data.first()
        val now = System.currentTimeMillis()
        val threshold = now + TimeUnit.MINUTES.toMillis(COUNTDOWN_THRESHOLD_MINUTES)

        // Find the next upcoming alarm within 10 minutes
        val nextAlarm = data.scheduled
            .filter { it.triggerAt > now && it.triggerAt <= threshold }
            .minByOrNull { it.triggerAt }

        if (nextAlarm != null) {
            showOrUpdateNotification(c, nextAlarm, now)
            startCountdownUpdates(c, nextAlarm)
        } else {
            cancelNotification()
        }
    }

    private fun showOrUpdateNotification(context: Context, alarm: AlarmEntry, now: Long) {
        val remainingMillis = alarm.triggerAt - now
        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
        val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60

        val timeText = String.format(Locale.getDefault(), "%02d:%02d", remainingMinutes, remainingSeconds)
        val alarmTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.triggerAt))

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("鬧鐘即將響起")
            .setContentText("${alarm.label} • $timeText (剩餘)")
            .setSubText("為 $alarmTime 的鬧鐘備計")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun startCountdownUpdates(context: Context, alarm: AlarmEntry) {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMillis = alarm.triggerAt - now

                if (remainingMillis <= 0) {
                    // Alarm should have fired, cancel notification
                    cancelNotification()
                    break
                }

                // The alarm may have been deleted or changed while counting
                // down — re-check the stored schedule every tick so the
                // notification reflects reality.
                val stillScheduled = Store(context).data.first()
                    .scheduled.any { it.id == alarm.id }
                if (!stillScheduled) {
                    cancelNotification()
                    break
                }

                handler.post {
                    showOrUpdateNotification(context, alarm, now)
                }
                delay(1000)
            }
        }
    }

    private fun cancelNotification() {
        updateJob?.cancel()
        updateJob = null
        val c = appContext ?: return
        runCatching { NotificationManagerCompat.from(c).cancel(NOTIFICATION_ID) }
    }
}
