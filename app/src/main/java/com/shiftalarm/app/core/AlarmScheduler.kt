package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shiftalarm.app.data.AlarmEntry

object AlarmScheduler {

    private fun pending(context: Context, entry: AlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, entry.id)
        }
        return PendingIntent.getBroadcast(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun pendingActivity(context: Context, entry: AlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, entry.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // For user-facing alarms (work and normal), use setAlarmClock for better doze mode support
        if (entry.kind in listOf("work", "normal") && !entry.isSnooze) {
            val info = AlarmManager.AlarmClockInfo(entry.triggerAt, pendingActivity(context, entry))
            try {
                am.setAlarmClock(info, pending(context, entry))
            } catch (e: SecurityException) {
                // Fallback to exact alarm if setAlarmClock fails
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry))
                } catch (e2: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry))
                }
            }
        } else {
            // For snooze and test alarms, use exact alarm
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry))
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry))
            }
        }
    }

    fun cancel(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, entry))
    }
}
