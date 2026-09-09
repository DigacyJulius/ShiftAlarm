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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CountdownNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "upcoming_alarm"
        const val NOTIFICATION_ID = 99999
        private const val COUNTDOWN_THRESHOLD_MINUTES = 10L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    private var currentAlarm: AlarmEntry? = null
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "\u5373\u5c07\u9b27\u9418",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "\u986f\u793a\u5373\u5c07\u97ff\u8d77\u7684\u9b27\u9418"
            nm.createNotificationChannel(channel)
        }
    }

    suspend fun checkAndShowCountdown() {
        // Cancel any existing update job
        updateJob?.cancel()
        
        val store = Store(context)
        val data = store.data.first()
        val now = System.currentTimeMillis()
        val threshold = now + TimeUnit.MINUTES.toMillis(COUNTDOWN_THRESHOLD_MINUTES)
        
        // Find the next upcoming alarm within 10 minutes
        val nextAlarm = data.scheduled
            .filter { it.triggerAt > now && it.triggerAt <= threshold }
            .minByOrNull { it.triggerAt }
            
        if (nextAlarm != null) {
            currentAlarm = nextAlarm
            showOrUpdateNotification(nextAlarm, now)
            startCountdownUpdates(nextAlarm)
        } else {
            currentAlarm = null
            cancelNotification()
        }
    }

    private fun showOrUpdateNotification(alarm: AlarmEntry, now: Long) {
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
            .setContentTitle("\u9b27\u9418\u5373\u5c07\u97ff\u8d77")
            .setContentText("${alarm.label} \u2022 $timeText (\u5269\u9918)")
            .setSubText("\u70ba ${alarmTime} \u7684\u9b27\u9418\u5099\u8a08")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun startCountdownUpdates(alarm: AlarmEntry) {
        updateJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMillis = alarm.triggerAt - now
                
                if (remainingMillis <= 0) {
                    // Alarm should have fired, cancel notification
                    cancelNotification()
                    break
                }
                
                if (remainingMillis <= TimeUnit.MINUTES.toMillis(COUNTDOWN_THRESHOLD_MINUTES)) {
                    // Update notification on main thread
                    handler.post {
                        showOrUpdateNotification(alarm, now)
                    }
                    
                    // Wait 1 second before next update (but don't update too frequently)
                    kotlinx.coroutines.delay(1000)
                } else {
                    // Alarm is no longer in countdown range
                    cancelNotification()
                    break
                }
            }
        }
    }

    private fun cancelNotification() {
        updateJob?.cancel()
        updateJob = null
        currentAlarm = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun onDestroy() {
        cancelNotification()
    }
}