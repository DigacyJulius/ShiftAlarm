package com.shiftalarm.app.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.shiftalarm.app.FIRE_ALARM"
        const val EXTRA_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id == -1L) return

        AlarmRingingActivity.ensureChannel(context)

        val launch = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullPi = PendingIntent.getActivity(
            context, id.toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, AlarmRingingActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("\u9b27\u9418\u97ff\u8d77")
            .setContentText("\u9ede\u6309\u67e5\u770b")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.toInt(), notif)
        } catch (e: SecurityException) {
            // notification permission not granted; full-screen intent still attempted below
        }
        runCatching { context.startActivity(launch) }
    }
}
